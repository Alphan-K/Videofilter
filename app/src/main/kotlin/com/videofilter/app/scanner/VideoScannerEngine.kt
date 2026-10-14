package com.videofilter.app.scanner

import android.content.Context
import android.os.Environment
import com.videofilter.app.data.VideoDao
import com.videofilter.app.data.VideoEntity
import com.videofilter.app.data.toEntity
import com.videofilter.app.model.MoveSuccessItem
import com.videofilter.app.model.VideoContainerFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

/** 扫描模式 */
enum class ScanMode {
    /** 增量：仅对新增/被修改的文件跑多媒体解析，其余复用数据库结果 */
    INCREMENTAL,

    /** 全量：忽略缓存，对所有视频重新解析（用于强制刷新元数据） */
    FULL
}

/** 扫描阶段 */
enum class ScanPhase { TRAVERSING, PARSING }

/** 一次扫描的 Diff 统计 */
data class ScanStats(
    val mode: ScanMode,
    val scope: ScanScope,
    val totalOnDisk: Int,
    val unchanged: Int,
    val toParse: Int,
    val parsedOk: Int,
    val deleted: Int,
    val elapsedMs: Long
)

sealed interface ScanEvent {
    data class Progress(
        val phase: ScanPhase,
        val processedFiles: Int,
        val currentPath: String
    ) : ScanEvent

    data class Completed(val stats: ScanStats) : ScanEvent
    data class Failed(val cause: Throwable) : ScanEvent
}

/**
 * 全盘视频扫描引擎（带增量 Diff）。
 *
 * 核心思想：**用微秒级的 Linux stat 属性比对，换取重型的多媒体解码开销**。
 * 遍历阶段只读 `File.length()` / `File.lastModified()`（底层就是 stat 系统调用），
 * 只有指纹发生变化的文件才进入 MediaExtractor / MediaMetadataRetriever 解析管道。
 *
 * 解析结果统一写入 Room；UI 通过观察 Room Flow 呈现，
 * 因此扫描过程本身不再需要向 UI 逐个推送条目。
 */
class VideoScannerEngine(
    private val context: Context,
    private val videoDao: VideoDao
) {

    fun scan(
        mode: ScanMode = ScanMode.INCREMENTAL,
        scope: ScanScope = ScanScope.ALL
    ): Flow<ScanEvent> = flow {
        val startedAt = System.currentTimeMillis()
        try {
            // ---------- 1. 载入数据库指纹快照 ----------
            val dbFingerprints = videoDao.getAllMetadataSnapshots()
            val dbPaths = HashSet<String>(dbFingerprints.size * 2)
            val unchangedIndex: Map<String, Pair<Long, Long>> =
                if (mode == ScanMode.INCREMENTAL) {
                    HashMap<String, Pair<Long, Long>>(dbFingerprints.size * 2).apply {
                        for (fp in dbFingerprints) {
                            dbPaths.add(fp.path)
                            put(fp.path, fp.sizeBytes to fp.lastModified)
                        }
                    }
                } else {
                    for (fp in dbFingerprints) dbPaths.add(fp.path)
                    emptyMap()
                }

            // ---------- 2. 解析本次要遍历的根目录 ----------
            // 限定范围扫描时只解析重点目录；全盘扫描才做多存储卷（含 SD 卡）根解析
            val roots = scope.resolveRelativeRoots()
                ?: resolveStorageRoots()

            // 关键：只允许清理「本次真正遍历过的目录树内」的失效记录。
            // 否则限定范围扫描会把范围外的记录全部误判为已删除。
            val scannedRootPrefixes = roots.map { it.absolutePath.trimEnd('/') + "/" }

            // ---------- 3. 轻量遍历：只读 stat，不做任何解码 ----------
            val diskPaths = HashSet<String>(1024)
            val toParse = ArrayList<File>(128)
            var scanned = 0L
            var unchanged = 0L

            for (root in roots) {
                if (!root.exists() || !root.canRead()) continue

                val sequence = root.walkTopDown().onEnter { shouldEnterDirectory(it) }
                for (file in sequence) {
                    if (!currentCoroutineContext().isActive) return@flow
                    if (!file.isFile) continue

                    scanned++
                    if (scanned % TRAVERSE_PROGRESS_STEP == 0L) {
                        emit(
                            ScanEvent.Progress(
                                phase = ScanPhase.TRAVERSING,
                                processedFiles = scanned.toInt(),
                                currentPath = file.parent ?: file.name
                            )
                        )
                    }

                    val extension = file.extension.lowercase(Locale.ROOT)
                    if (extension !in VideoContainerFormat.allSupportedExtensions) continue

                    val path = file.absolutePath
                    diskPaths.add(path)

                    val cached = unchangedIndex[path]
                    val isUnchanged = cached != null &&
                        cached.first == file.length() &&
                        cached.second == file.lastModified()

                    if (isUnchanged) {
                        unchanged++
                    } else {
                        toParse.add(file)
                    }
                }
            }

            // ---------- 4. 清理磁盘上已消失的记录（仅限已遍历范围） ----------
            val deletedPaths = dbPaths.filterTo(HashSet()) { dbPath ->
                dbPath !in diskPaths && scannedRootPrefixes.any { dbPath.startsWith(it) }
            }
            if (deletedPaths.isNotEmpty()) {
                // 按 900 条一批切分，规避 SQLite 单语句 999 个绑定参数上限
                videoDao.safeDeleteByPathsInBatches(deletedPaths.toList())
            }

            // ---------- 4. 仅对变更/新增文件执行重型解析 ----------
            val batch = ArrayList<VideoEntity>(DB_BATCH_SIZE)
            var parsedOk = 0
            var handled = 0

            for (index in toParse.indices) {
                if (!currentCoroutineContext().isActive) return@flow
                val file = toParse[index]

                val containerFormat =
                    VideoContainerFormat.fromExtension(file.extension.lowercase(Locale.ROOT))
                if (containerFormat != null) {
                    val item = VideoMetadataParser.parse(file, containerFormat)
                    if (item != null) {
                        batch.add(item.toEntity())
                        parsedOk++
                    }
                }
                handled++

                if (batch.size >= DB_BATCH_SIZE) {
                    videoDao.insertOrUpdate(batch.toList())
                    batch.clear()
                }
                if (handled % PARSE_PROGRESS_STEP == 0) {
                    emit(
                        ScanEvent.Progress(
                            phase = ScanPhase.PARSING,
                            processedFiles = handled,
                            currentPath = file.name
                        )
                    )
                }
            }
            if (batch.isNotEmpty()) {
                videoDao.insertOrUpdate(batch)
            }

            emit(
                ScanEvent.Completed(
                    ScanStats(
                        mode = mode,
                        scope = scope,
                        totalOnDisk = diskPaths.size,
                        unchanged = unchanged.toInt(),
                        toParse = toParse.size,
                        parsedOk = parsedOk,
                        deleted = deletedPaths.size,
                        elapsedMs = System.currentTimeMillis() - startedAt
                    )
                )
            )
        } catch (t: Throwable) {
            emit(ScanEvent.Failed(t))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 文件移动完成后的**热更新入口**。
     *
     * 直接改写数据库中的主键路径，重型的码率 / 编码 / 分辨率 / 时长等元数据全部保留，
     * 完全不触发 MediaExtractor / MediaMetadataRetriever 的二次解码（通常 <10ms）。
     */
    suspend fun syncMovedVideos(movedItems: List<MoveSuccessItem>) {
        if (movedItems.isEmpty()) return
        videoDao.batchUpdateVideoPaths(movedItems)
    }

    /**
     * 解析当前设备的所有物理存储根路径（主内部存储 + 外部挂载的 MicroSD 卡）
     */
    private fun resolveStorageRoots(): List<File> {
        val roots = mutableListOf<File>()

        // 1. 内部共享存储主入口 (/storage/emulated/0)
        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage != null) {
            roots.add(primaryStorage)
        }

        // 2. 通过系统 API 解析潜在的物理 SD 卡入口（minSdk 26 起平台原生 API 即可用）
        val externalFilesDirs = context.getExternalFilesDirs(null)
        for (dir in externalFilesDirs) {
            if (dir != null) {
                // 递归回溯寻找类似 /storage/XXXX-XXXX 挂载点
                var parent = dir.parentFile
                while (parent != null && parent.parentFile != null && parent.parentFile?.name != "storage") {
                    parent = parent.parentFile
                }
                if (parent != null && parent != primaryStorage && !roots.contains(parent)) {
                    roots.add(parent)
                }
            }
        }

        return roots
    }

    /**
     * 剪枝判断：避免扫描损坏虚拟路径或死循环符号链接
     */
    private fun shouldEnterDirectory(dir: File): Boolean {
        val dirName = dir.name

        // 跳过以点号开头的隐藏文件夹（大部分包含缓存/系统临时数据）
        if (dirName.startsWith(".") && dirName != ".") {
            return false
        }

        // 跳过 Android/data 和 Android/obb 等受限系统沙盒，避免权限报错并节约扫描耗时
        if (dirName.equals("data", ignoreCase = true) || dirName.equals("obb", ignoreCase = true)) {
            val parentName = dir.parentFile?.name
            if (parentName.equals("Android", ignoreCase = true)) {
                return false
            }
        }

        return true
    }

    private companion object {
        const val TRAVERSE_PROGRESS_STEP = 200L
        const val PARSE_PROGRESS_STEP = 5
        const val DB_BATCH_SIZE = 32
    }
}
