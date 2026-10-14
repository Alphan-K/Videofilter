package com.videofilter.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 每个「归档目录分组」的上次扫描时间。
 *
 * 用于在目录入口上标注数据新鲜度，并提示「当前查看的目录可能不是最新数据」。
 */
@Entity(tableName = "scan_records")
data class ScanRecordEntity(
    /** [com.videofilter.app.scanner.VideoFolder] 的 name */
    @PrimaryKey val folder: String,
    val lastScannedAt: Long
)

@Dao
interface ScanRecordDao {

    @Query("SELECT * FROM scan_records")
    fun observeAll(): Flow<List<ScanRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<ScanRecordEntity>)

    @Query("DELETE FROM scan_records")
    suspend fun clearAll()
}
