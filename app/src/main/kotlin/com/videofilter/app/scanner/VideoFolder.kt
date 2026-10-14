package com.videofilter.app.scanner

/**
 * 视频所属的「目录分组」——用于把扫描结果按物理目录归档展示。
 *
 * 与 [ScanScope] 的区别：
 * - [ScanScope] 是**扫描范围**（用户决定这次去翻哪些目录，含「全盘」这个虚拟范围）；
 * - [VideoFolder] 是**归档分组**（视频实际躺在哪个重点目录里，或落在重点目录之外归为「其他」）。
 *
 * 两者通过 [ScanScope.targetFolders] 关联：一次扫描会刷新其覆盖到的所有分组的「上次扫描时间」。
 */
enum class VideoFolder(
    val displayName: String,
    /** 相对外部存储根的目录名；[OTHER] 为 null 表示「不属于任何重点目录」 */
    val relativePath: String?
) {
    DCIM("DCIM", "DCIM"),
    PICTURES("Pictures", "Pictures"),
    MOVIES("Movies", "Movies"),
    DOWNLOAD("Download", "Download"),
    OTHER("其他", null);

    /** 是否能作为独立的扫描目标（「其他」只能靠全盘扫描刷新） */
    val isScannable: Boolean get() = relativePath != null

    /**
     * 「扫描完成后自动询问是否移动」的出厂默认值。
     *
     * 只默认打开 DCIM：相机拍摄的视频是绝大多数人真正要归档的对象；
     * 其余目录（尤其是 Download、其他）来源杂乱，默认静默以免打扰。
     */
    val defaultAutoMoveEnabled: Boolean get() = this == DCIM

    companion object {
        /** 由持久化的 name 反查（容错：找不到则返回 null） */
        fun fromNameOrNull(name: String): VideoFolder? =
            entries.firstOrNull { it.name == name }

        /**
         * 由文件绝对路径推断所属分组。
         *
         * 支持内部存储 `/storage/emulated/0/<目录>/...`
         * 与物理 SD 卡 `/storage/<卷标>/<目录>/...` 两种布局。
         */
        fun ofPath(path: String): VideoFolder {
            val segments = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) return OTHER

            val anchor = segments.indexOfFirst { it.equals("storage", ignoreCase = true) }
            val folderSegment = if (anchor >= 0) {
                var index = anchor + 1
                index += if (segments.getOrNull(index)?.equals("emulated", ignoreCase = true) == true) {
                    // /storage/emulated/0/<目录>/... —— 跳过 emulated 与用户号
                    2
                } else {
                    // /storage/<卷标>/<目录>/... —— 跳过卷标
                    1
                }
                segments.getOrNull(index).orEmpty()
            } else {
                // 非标准挂载点：退化为取首段
                segments.firstOrNull().orEmpty()
            }

            return entries.firstOrNull { folder ->
                folder.relativePath != null && folder.relativePath.equals(folderSegment, ignoreCase = true)
            } ?: OTHER
        }
    }
}
