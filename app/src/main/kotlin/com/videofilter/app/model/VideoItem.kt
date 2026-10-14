package com.videofilter.app.model

import java.io.File
import java.util.UUID

/**
 * 视频实体数据
 */
data class VideoItem(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val bitrateKbps: Long,
    val width: Int,
    val height: Int,
    val containerFormat: VideoContainerFormat,
    val codec: VideoCodec,
    val rawMimeType: String?,
    val lastModified: Long
) {
    val file: File get() = File(path)

    companion object {
        fun stableIdFromPath(path: String): String {
            return UUID.nameUUIDFromBytes(path.toByteArray()).toString()
        }
    }
}
