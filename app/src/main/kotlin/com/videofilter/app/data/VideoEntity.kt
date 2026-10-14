package com.videofilter.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.videofilter.app.model.VideoItem

/**
 * 视频元数据持久化实体。
 *
 * 关键设计：`sizeBytes` + `lastModified` 共同构成**增量比对指纹**。
 * 只要这两个值与上次入库时一致，就认为文件内容未变，可完全跳过 JNI 层解码。
 */
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long, // 核心比对特征 1
    val durationMs: Long,
    val bitrateKbps: Long,
    val width: Int,
    val height: Int,
    val containerFormat: String,
    val codec: String,
    val rawMimeType: String?
)

/**
 * 轻量指纹投影：增量扫描时只从数据库捞出这三列，
 * 避免把整行实体加载进内存（数千条时差异显著）。
 */
data class VideoFingerprint(
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)

/**
 * 磁盘扫描出的指纹（用于与数据库比对）
 */
data class DiskFingerprint(
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)

/* ============================ 实体 <-> 领域模型 映射 ============================ */

/**
 * 枚举以 name 字符串入库而非序号，避免后续枚举顺序调整导致历史数据错位。
 * 反序列化时做容错：找不到对应枚举则回退到兜底值，绝不抛异常。
 */
fun VideoEntity.toModel(): VideoItem {
    return VideoItem(
        id = VideoItem.stableIdFromPath(path),
        name = name,
        path = path,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        bitrateKbps = bitrateKbps,
        width = width,
        height = height,
        containerFormat = decodeContainer(containerFormat),
        codec = decodeCodec(codec),
        rawMimeType = rawMimeType,
        lastModified = lastModified
    )
}

fun VideoItem.toEntity(): VideoEntity {
    return VideoEntity(
        path = path,
        name = name,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        durationMs = durationMs,
        bitrateKbps = bitrateKbps,
        width = width,
        height = height,
        containerFormat = containerFormat.name,
        codec = codec.name,
        rawMimeType = rawMimeType
    )
}
