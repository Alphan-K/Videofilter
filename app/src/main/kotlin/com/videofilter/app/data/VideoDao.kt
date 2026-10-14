package com.videofilter.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.videofilter.app.model.MoveSuccessItem
import kotlinx.coroutines.flow.Flow
import java.io.File

@Dao
interface VideoDao {

    /**
     * 全量观察：冷启动时 Room 立刻吐出上次落库的数据，
     * UI 无需等待任何文件遍历即可呈现（0 延迟）。
     */
    @Query("SELECT * FROM videos ORDER BY name ASC")
    fun observeAll(): Flow<List<VideoEntity>>

    /**
     * 只取增量比对所需的三列，避免整行加载。
     */
    @Query("SELECT path, sizeBytes, lastModified FROM videos")
    suspend fun getAllMetadataSnapshots(): List<VideoFingerprint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(items: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("DELETE FROM videos WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM videos")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun count(): Int

    /* ==================== 文件移动后的主键热更新 ==================== */

    /**
     * 原子更新单个视频的主键路径与基础文件属性。
     *
     * `path` 是主键，无法用 `@Update`（它会拿传入实体的新主键去匹配旧记录，永远匹配不上），
     * 必须走原生 SQL 显式指定 `WHERE path = :oldPath`。
     * 码率 / 编码 / 分辨率 / 时长等重型元数据**原样保留**，避免二次 JNI 解码。
     */
    @Query(
        """
        UPDATE videos 
        SET path = :newPath, 
            name = :newName, 
            sizeBytes = :sizeBytes, 
            lastModified = :lastModified 
        WHERE path = :oldPath
        """
    )
    suspend fun updatePathInternal(
        oldPath: String,
        newPath: String,
        newName: String,
        sizeBytes: Long,
        lastModified: Long
    ): Int

    /**
     * 事务批量更新移动成功的记录。
     * 单次事务提交只触发一次 Room Invalidation，UI 列表一次性平滑刷新。
     */
    @Transaction
    suspend fun batchUpdateVideoPaths(items: List<MoveSuccessItem>) {
        if (items.isEmpty()) return
        for (item in items) {
            // 防御：目标路径若存在历史脏记录，先剔除，避免主键冲突
            deleteByPath(item.newPath)

            updatePathInternal(
                oldPath = item.oldPath,
                newPath = item.newPath,
                newName = File(item.newPath).name,
                sizeBytes = item.sizeBytes,
                lastModified = item.lastModified
            )
            // 若旧记录不在库中（未索引即被移动），不报错，等后续增量扫描补录
        }
    }

    /**
     * 分批删除，规避 SQLite 单语句 999 个绑定参数上限。
     */
    @Transaction
    suspend fun safeDeleteByPathsInBatches(paths: List<String>, batchSize: Int = 900) {
        paths.chunked(batchSize).forEach { chunk ->
            deleteByPaths(chunk)
        }
    }
}
