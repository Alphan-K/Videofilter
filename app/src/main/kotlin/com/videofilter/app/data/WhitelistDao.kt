package com.videofilter.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {

    @Query("SELECT * FROM whitelist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WhitelistEntity>>

    /** 仅取路径集合，用于主列表排除（避免把整行加载进内存） */
    @Query("SELECT path FROM whitelist")
    fun observePaths(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WhitelistEntity>)

    @Query("DELETE FROM whitelist WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    /** 分批删除，规避 SQLite 单语句 999 个绑定参数上限 */
    @Transaction
    suspend fun safeDeleteByPathsInBatches(paths: List<String>, batchSize: Int = 900) {
        paths.chunked(batchSize).forEach { chunk -> deleteByPaths(chunk) }
    }

    @Query("SELECT COUNT(*) FROM whitelist")
    suspend fun count(): Int
}
