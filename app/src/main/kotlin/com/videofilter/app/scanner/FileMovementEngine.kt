package com.videofilter.app.scanner

import android.content.Context
import android.media.MediaScannerConnection
import com.videofilter.app.model.MoveFailedItem
import com.videofilter.app.model.MoveProgressEvent
import com.videofilter.app.model.MoveReport
import com.videofilter.app.model.MoveSuccessItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 工业级文件安全移动引擎。
 *
 * 移动策略三级降级：
 *   1. NIO `ATOMIC_MOVE` —— 同分区内仅改元数据，毫秒级；
 *   2. `File.renameTo` —— 兼容部分不实现 NIO 原子移动的文件系统；
 *   3. `FileChannel.transferTo` 流式拷贝 + **字节长度校验** + 删除源文件
 *      —— 用于跨存储卷（`EXDEV`）场景；任一步失败都会回滚目标残卷。
 *
 * 前置安全网：
 *   - 目标目录可创建性校验；
 *   - 磁盘可用空间预检（额外预留 50MB 缓冲，避免打满导致系统卡死）；
 *   - 重名防覆盖递增命名（`name.mp4` -> `name (1).mp4`）。
 */
class FileMovementEngine(private val context: Context) {

    /**
     * 批量移动文件流式任务
     */
    fun moveVideos(
        sourcePaths: List<String>,
        targetDirPath: String
    ): Flow<MoveProgressEvent> = flow {
        emit(MoveProgressEvent.Validating("正在核验磁盘可用空间及目标目录…"))

        val targetDir = File(targetDirPath)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            emit(
                MoveProgressEvent.Completed(
                    MoveReport(
                        totalRequested = sourcePaths.size,
                        successList = emptyList(),
                        failedList = sourcePaths.map {
                            MoveFailedItem(it, "无法创建目标文件夹：${targetDir.absolutePath}")
                        },
                        skippedCount = 0
                    )
                )
            )
            return@flow
        }

        // ---------- 1. 过滤合法源文件并统计总体积 ----------
        val validSourceFiles = mutableListOf<File>()
        var requiredBytes = 0L
        for (path in sourcePaths) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                validSourceFiles.add(file)
                requiredBytes += file.length()
            }
        }

        // ---------- 2. 磁盘可用空间预检 ----------
        // 有效源文件为空时跳过预检：否则在接近写满的设备上会误报「空间不足」，
        // 而实际上根本没有文件要写。
        val availableBytes = targetDir.usableSpace
        val requiredWithBuffer = requiredBytes + SAFETY_BUFFER_BYTES
        if (requiredBytes > 0 && availableBytes in 1 until requiredWithBuffer) {
            emit(
                MoveProgressEvent.InsufficientStorage(
                    requiredBytes = requiredWithBuffer,
                    availableBytes = availableBytes
                )
            )
            return@flow
        }

        val successItems = mutableListOf<MoveSuccessItem>()
        val failedItems = mutableListOf<MoveFailedItem>()
        var skippedCount = 0
        var processedBytes = 0L
        val totalCount = validSourceFiles.size
        val pathsToRescan = mutableListOf<String>()

        // ---------- 3. 逐个文件执行安全迁移 ----------
        for ((index, sourceFile) in validSourceFiles.withIndex()) {
            if (!currentCoroutineContext().isActive) break

            emit(
                MoveProgressEvent.Processing(
                    currentIndex = index + 1,
                    totalCount = totalCount,
                    currentFileName = sourceFile.name,
                    processedBytes = processedBytes,
                    totalBytes = requiredBytes
                )
            )

            // 源目录与目标目录相同则跳过，不做无意义搬运
            if (runCatching { sourceFile.parentFile?.canonicalPath == targetDir.canonicalPath }
                    .getOrDefault(false)
            ) {
                skippedCount++
                processedBytes += sourceFile.length()
                continue
            }

            try {
                val destinationFile = resolveUniqueDestinationFile(targetDir, sourceFile.name)
                val moved = executeSafeMove(sourceFile, destinationFile)

                if (moved) {
                    successItems.add(
                        MoveSuccessItem(
                            oldPath = sourceFile.absolutePath,
                            newPath = destinationFile.absolutePath,
                            sizeBytes = destinationFile.length(),
                            lastModified = destinationFile.lastModified()
                        )
                    )
                    pathsToRescan.add(sourceFile.absolutePath)
                    pathsToRescan.add(destinationFile.absolutePath)
                } else {
                    failedItems.add(MoveFailedItem(sourceFile.absolutePath, "底层文件移动失败（IO 错误）"))
                }
            } catch (e: Exception) {
                failedItems.add(MoveFailedItem(sourceFile.absolutePath, e.localizedMessage ?: "未知错误"))
            }

            processedBytes += sourceFile.length()
        }

        // ---------- 4. 通知系统媒体库释放旧索引、建立新索引 ----------
        syncMediaStore(pathsToRescan)

        emit(
            MoveProgressEvent.Completed(
                MoveReport(
                    totalRequested = sourcePaths.size,
                    successList = successItems,
                    failedList = failedItems,
                    skippedCount = skippedCount
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * 安全移动：同卷原子迁移 -> renameTo -> 流式拷贝校验
     */
    private fun executeSafeMove(source: File, destination: File): Boolean {
        // 尝试 1：NIO 原子移动（同分区内效率最高，仅修改元数据）
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return true
        } catch (_: Exception) {
            // 抛出 AtomicMoveNotSupportedException 或 EXDEV（跨存储设备）时进入下一级
        }

        // 尝试 2：File 原生重命名
        try {
            if (source.renameTo(destination)) return true
        } catch (_: Exception) {
            // 继续降级
        }

        // 尝试 3：跨分区流式通道
        return transferAndDelete(source, destination)
    }

    /**
     * 跨存储卷物理流式拷贝 + 完整性校验 + 源文件删除。
     *
     * 先写入同目录下的 `.part` 暂存文件，校验通过后才改名为正式文件。
     * 这样即使进程在拷贝途中被系统杀掉，目标位置也**不会留下半截文件**
     * （半截文件带真实扩展名，用户与相册都无法察觉，属于静默数据事故）；
     * 而 `.part` 不是受支持的视频扩展名，不会被扫描器收录，最多占一点空间。
     */
    private fun transferAndDelete(source: File, destination: File): Boolean {
        val staging = File(destination.parentFile, destination.name + STAGING_SUFFIX)
        if (staging.exists()) staging.delete()

        var copyVerified = false
        try {
            FileInputStream(source).channel.use { inChannel ->
                FileOutputStream(staging).channel.use { outChannel ->
                    val size = inChannel.size()
                    var transferred = 0L

                    // 首选零拷贝通道
                    while (transferred < size) {
                        val count = inChannel.transferTo(transferred, size - transferred, outChannel)
                        if (count <= 0L) break
                        transferred += count
                    }

                    // 部分文件系统实现下 transferTo 会提前返回 0，用缓冲区兜底补齐
                    if (transferred < size) {
                        inChannel.position(transferred)
                        val buffer = ByteBuffer.allocate(TRANSFER_BUFFER_SIZE)
                        while (inChannel.read(buffer) > 0) {
                            buffer.flip()
                            while (buffer.hasRemaining()) {
                                outChannel.write(buffer)
                            }
                            buffer.clear()
                        }
                    }
                }
            }

            // 完整性校验：字节长度必须严格一致，再原子改名
            if (staging.exists() && staging.length() == source.length()) {
                if (staging.renameTo(destination)) {
                    copyVerified = source.delete()
                    if (!copyVerified) {
                        // 源文件删不掉（权限/占用）时回滚目标文件，避免同一内容分裂成两份
                        destination.delete()
                    }
                } else {
                    staging.delete()
                }
            } else {
                staging.delete()
            }
        } catch (e: IOException) {
            if (staging.exists()) staging.delete()
            if (destination.exists()) destination.delete()
            throw e
        }
        return copyVerified
    }

    /**
     * 防重名冲突命名器：Name.mp4 -> Name (1).mp4 -> Name (2).mp4
     */
    private fun resolveUniqueDestinationFile(targetDir: File, originalName: String): File {
        var candidate = File(targetDir, originalName)
        if (!candidate.exists()) return candidate

        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        var index = 1
        while (candidate.exists()) {
            candidate = File(targetDir, "$baseName ($index)$extension")
            index++
            if (index > MAX_NAME_RETRY) break
        }
        return candidate
    }

    /**
     * 触发系统 MediaStore 刷新（旧路径注销 + 新路径建索引）
     */
    private suspend fun syncMediaStore(paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        runCatching {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> }
        }
    }

    private companion object {
        const val SAFETY_BUFFER_BYTES = 50L * 1024L * 1024L
        const val TRANSFER_BUFFER_SIZE = 1024 * 1024 // 1MB
        const val MAX_NAME_RETRY = 10_000

        /** 跨卷拷贝的暂存后缀：不是受支持的视频扩展名，不会被扫描器收录 */
        const val STAGING_SUFFIX = ".part"
    }
}
