package com.videofilter.app.scanner

import android.os.Environment
import java.io.File

/**
 * 扫描范围。
 *
 * 默认只遍历若干「重点目录」，避免一次操作把整块存储翻一遍带来的功耗与耗时；
 * 需要时仍可切到全盘。
 */
enum class ScanScope(
    val displayName: String,
    /** 相对于外部存储根目录的子路径；[ALL] 为空表示走全盘根解析 */
    private val relativePaths: List<String>,
    /** 本次扫描会刷新扫描时间的归档分组 */
    val targetFolders: List<VideoFolder>
) {
    ALL(
        displayName = "全盘",
        relativePaths = emptyList(),
        targetFolders = listOf(
            VideoFolder.DCIM, VideoFolder.PICTURES,
            VideoFolder.MOVIES, VideoFolder.DOWNLOAD, VideoFolder.OTHER
        )
    ),
    DCIM("DCIM", listOf("DCIM"), listOf(VideoFolder.DCIM)),
    PICTURES("Pictures", listOf("Pictures"), listOf(VideoFolder.PICTURES)),
    MOVIES("Movies", listOf("Movies"), listOf(VideoFolder.MOVIES)),
    DOWNLOAD("Download", listOf("Download"), listOf(VideoFolder.DOWNLOAD));

    val isWholeDisk: Boolean get() = this == ALL

    /**
     * 解析为该范围对应的物理目录列表。
     * [ALL] 返回 null，交由引擎去做多存储卷（含 SD 卡）的完整根解析。
     */
    fun resolveRelativeRoots(): List<File>? {
        if (isWholeDisk) return null
        val base = Environment.getExternalStorageDirectory() ?: return emptyList()
        return relativePaths.map { File(base, it) }
    }

    companion object {
        /** 归档分组 -> 对应的扫描范围（[VideoFolder.OTHER] 只能靠全盘刷新） */
        fun forFolder(folder: VideoFolder): ScanScope = when (folder) {
            VideoFolder.DCIM -> DCIM
            VideoFolder.PICTURES -> PICTURES
            VideoFolder.MOVIES -> MOVIES
            VideoFolder.DOWNLOAD -> DOWNLOAD
            VideoFolder.OTHER -> ALL
        }
    }
}
