package com.videofilter.app.scanner

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.videofilter.app.model.VideoCodec
import com.videofilter.app.model.VideoContainerFormat
import com.videofilter.app.model.VideoItem
import java.io.File

object VideoMetadataParser {

    /**
     * 深度解析指定视频文件的元数据
     * 若文件无法作为视频解析，则返回 null
     */
    fun parse(file: File, containerFormat: VideoContainerFormat): VideoItem? {
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return null
        }

        val path = file.absolutePath
        var extractedMime: String? = null

        // 步骤 1：使用 MediaExtractor 精确解复用，获取视频轨真实 Codec MIME
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("video/", ignoreCase = true)) {
                    extractedMime = mime
                    break
                }
            }
        } catch (_: Exception) {
            // 部分封装如 RMVB/部分 AVI，系统原生 extractor 无法解复用，忽略并进入下一步
        } finally {
            runCatching { extractor.release() }
        }

        // 步骤 2：使用 MediaMetadataRetriever 提取基础视讯信息
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            // 计算码率（优先使用原生，原生缺失则通过 (bytes * 8) / durationMs 闭环推导）
            val bitrateKbps = resolveBitrateKbps(
                rawBitrateStr = bitrateStr,
                durationMs = durationMs,
                fileLengthBytes = file.length()
            )

            val codec = VideoCodec.fromMimeType(extractedMime)

            VideoItem(
                id = VideoItem.stableIdFromPath(path),
                name = file.name,
                path = path,
                sizeBytes = file.length(),
                durationMs = durationMs,
                bitrateKbps = bitrateKbps,
                width = width,
                height = height,
                containerFormat = containerFormat,
                codec = codec,
                rawMimeType = extractedMime,
                lastModified = file.lastModified()
            )
        } catch (_: Exception) {
            // 拦截损坏视频或未授权访问
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 码率推导机制：统一转换为 kbps 整数单位
     */
    private fun resolveBitrateKbps(
        rawBitrateStr: String?,
        durationMs: Long,
        fileLengthBytes: Long
    ): Long {
        val nativeBps = rawBitrateStr?.toLongOrNull() ?: 0L
        if (nativeBps > 0L) {
            // 原生 METADATA_KEY_BITRATE 的单位是 bps，除以 1000 得到 kbps
            return nativeBps / 1000L
        }

        // 兜底反推计算：当缺少原生标记但存在有效播放时长时
        if (durationMs > 0L && fileLengthBytes > 0L) {
            val estimatedKbps = (fileLengthBytes * 8L) / durationMs
            return estimatedKbps.coerceAtLeast(1L)
        }

        return 0L
    }
}
