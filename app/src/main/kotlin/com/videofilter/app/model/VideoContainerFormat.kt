package com.videofilter.app.model

import java.util.Locale

/**
 * 视频封装格式全集
 */
enum class VideoContainerFormat(val displayName: String, val extensions: Set<String>) {
    MP4("MP4", setOf("mp4")),
    MKV("MKV", setOf("mkv")),
    MOV("MOV", setOf("mov", "qt")),
    AVI("AVI", setOf("avi")),
    FLV("FLV", setOf("flv")),
    WMV("WMV", setOf("wmv")),
    WEBM("WebM", setOf("webm")),
    TS("TS", setOf("ts", "m2ts", "mts")),
    M4V("M4V", setOf("m4v")),
    THREE_GP("3GP", setOf("3gp", "3g2")),
    RMVB("RMVB", setOf("rmvb", "rm")),
    MPG("MPG", setOf("mpg", "mpeg")),
    VOB("VOB", setOf("vob"));

    companion object {
        private val EXTENSION_MAP = entries.flatMap { format ->
            format.extensions.map { ext -> ext.lowercase(Locale.ROOT) to format }
        }.toMap()

        /**
         * 获取所有受支持的扩展名集合（不带点）
         */
        val allSupportedExtensions: Set<String> = EXTENSION_MAP.keys

        fun fromExtension(extension: String): VideoContainerFormat? {
            return EXTENSION_MAP[extension.lowercase(Locale.ROOT)]
        }
    }
}
