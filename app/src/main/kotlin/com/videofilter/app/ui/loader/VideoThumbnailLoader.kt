package com.videofilter.app.ui.loader

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

object VideoThumbnailLoader {
    // 分配可用最大内存的 1/8 作为位图缓存
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // 限制同时并发提取帧的协程数，防止滑动时打满 CPU/IO
    private val decodeSemaphore = Semaphore(permits = 4)

    /**
     * 异步提取并降采样缩略图
     */
    suspend fun loadThumbnail(path: String, targetWidth: Int = 360, targetHeight: Int = 200): Bitmap? {
        // 1. 命中内存缓存直接返回
        memoryCache.get(path)?.let { return it }

        val file = File(path)
        if (!file.exists() || !file.canRead()) return null

        return withContext(Dispatchers.IO) {
            decodeSemaphore.withPermit {
                // 二次核查缓存（避免排队期间其他协程已完成解码）
                memoryCache.get(path)?.let { return@withPermit it }

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)

                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        // 硬件/系统级降采样解码，杜绝全尺寸大图暴涨内存
                        retriever.getScaledFrameAtTime(
                            1_000_000, // 抓取第 1 秒的关键帧
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            targetWidth,
                            targetHeight
                        )
                    } else {
                        // API 26 没有 getScaledFrameAtTime，frameAtTime 返回的是**全尺寸帧**。
                        // 4K 一帧 ARGB_8888 约 33MB，必须手动降采样后再入缓存，否则必然 OOM。
                        retriever.frameAtTime?.let { raw ->
                            downscale(raw, targetWidth, targetHeight)
                        }
                    }

                    if (bitmap != null) {
                        memoryCache.put(path, bitmap)
                    }
                    bitmap
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }
    }

    /**
     * 把全尺寸帧等比缩放到目标尺寸内。
     * 缩放成功后回收原图，避免同一帧在内存里存在两份。
     */
    private fun downscale(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (source.width <= maxWidth && source.height <= maxHeight) return source

        val ratio = minOf(
            maxWidth.toFloat() / source.width.toFloat(),
            maxHeight.toFloat() / source.height.toFloat()
        )
        val targetW = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (source.height * ratio).toInt().coerceAtLeast(1)

        return runCatching {
            val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
            if (scaled !== source) source.recycle()
            scaled
        }.getOrElse { source }
    }
}

/**
 * Compose 专用的生命周期感知缩略图装载器
 */
@Composable
fun rememberVideoThumbnail(path: String): Bitmap? {
    var thumbnail by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
        thumbnail = VideoThumbnailLoader.loadThumbnail(path)
    }

    return thumbnail
}
