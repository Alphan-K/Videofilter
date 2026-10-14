package com.videofilter.app.model

import java.util.Locale

/**
 * 视频编码格式枚举
 */
enum class VideoCodec(val displayName: String, val mimeTypes: Set<String>) {
    H264("H.264 / AVC", setOf("video/avc")),
    H265("H.265 / HEVC", setOf("video/hevc")),
    AV1("AV1", setOf("video/av01")),
    VP8("VP8", setOf("video/x-vnd.on2.vp8")),
    VP9("VP9", setOf("video/x-vnd.on2.vp9")),
    MPEG4("MPEG-4", setOf("video/mp4v-es")),
    VC1("VC-1", setOf("video/wvc1", "video/x-wmv")),
    UNKNOWN("其他/未知", emptySet());

    companion object {
        fun fromMimeType(mime: String?): VideoCodec {
            if (mime == null) return UNKNOWN
            val lowerMime = mime.lowercase(Locale.ROOT)
            return entries.firstOrNull { codec ->
                codec.mimeTypes.any { it.equals(lowerMime, ignoreCase = true) }
            } ?: UNKNOWN
        }
    }
}
