package com.videofilter.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.videofilter.app.model.VideoCodec
import com.videofilter.app.model.VideoContainerFormat
import com.videofilter.app.model.VideoItem

/**
 * 白名单实体 —— **用户资产，非派生数据**。
 *
 * 刻意与 `videos` 表分离，原因：
 * 1. `videos` 表是扫描结果的缓存，随时可能被全量重建 / 破坏性迁移清空；
 * 2. 白名单要求「永久」生效，即使对应文件被移走或删除，记录也必须保留，
 *    以便用户仍能在白名单视图里看到并自行清理。
 *
 * 因此这里冗余保存了一份元数据快照，使其不依赖 `videos` 表即可独立展示。
 */
@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val durationMs: Long,
    val bitrateKbps: Long,
    val width: Int,
    val height: Int,
    val containerFormat: String,
    val codec: String,
    val rawMimeType: String?,
    val addedAt: Long
)

fun WhitelistEntity.toModel(): VideoItem {
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

fun VideoItem.toWhitelistEntity(addedAt: Long = System.currentTimeMillis()): WhitelistEntity {
    return WhitelistEntity(
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
        rawMimeType = rawMimeType,
        addedAt = addedAt
    )
}

/* 枚举以 name 字符串入库，反序列化容错回退，避免顺序调整导致历史数据错位 */
internal fun decodeContainer(raw: String): VideoContainerFormat =
    runCatching { VideoContainerFormat.valueOf(raw) }.getOrDefault(VideoContainerFormat.MP4)

internal fun decodeCodec(raw: String): VideoCodec =
    runCatching { VideoCodec.valueOf(raw) }.getOrDefault(VideoCodec.UNKNOWN)
