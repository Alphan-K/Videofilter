package com.videofilter.app.model

/**
 * 筛选器全局状态模型
 */
data class VideoFilterState(
    // 选中的容器封装格式集合（默认全选）
    val selectedFormats: Set<VideoContainerFormat> = VideoContainerFormat.entries.toSet(),
    // 选中的视频编码集合（默认全选）
    val selectedCodecs: Set<VideoCodec> = VideoCodec.entries.toSet(),
    // 码率输入原始字符串（保持输入响应顺畅）
    val minBitrateInput: String = "",
    val maxBitrateInput: String = ""
) {
    // 码率数值安全转换（kbps）
    val minBitrateKbps: Long? get() = minBitrateInput.toLongOrNull()
    val maxBitrateKbps: Long? get() = maxBitrateInput.toLongOrNull()

    // 冲突检查：当上下限均填写且下限大于上限时为非法状态
    val isBitrateRangeInvalid: Boolean
        get() {
            val min = minBitrateKbps
            val max = maxBitrateKbps
            return min != null && max != null && min > max
        }

    // 全选状态判定
    val isAllFormatsSelected: Boolean
        get() = selectedFormats.size == VideoContainerFormat.entries.size

    val isAllCodecsSelected: Boolean
        get() = selectedCodecs.size == VideoCodec.entries.size

    /**
     * 把「记忆的默认码率」套用到空输入框上，得到真正参与筛选的状态。
     *
     * 语义：输入框里有值就以输入为准；输入框为空则沿用上次记忆的值（首次运行为 30000 / 无上限）。
     * 输入 0 表示显式不限。
     *
     * 注意：该结果**只用于过滤计算**，不要回灌给 UI，否则占位文字会变成实际文本。
     */
    fun withBitrateDefaults(defaultMinKbps: Long?, defaultMaxKbps: Long?): VideoFilterState {
        return copy(
            minBitrateInput = minBitrateInput.ifBlank { defaultMinKbps?.toString().orEmpty() },
            maxBitrateInput = maxBitrateInput.ifBlank { defaultMaxKbps?.toString().orEmpty() }
        )
    }

    /**
     * 判断单个视频是否命中当前所有筛选规则
     */
    fun matches(item: VideoItem): Boolean {
        // 1. 容器封装匹配
        if (item.containerFormat !in selectedFormats) return false

        // 2. 视频编码匹配
        if (item.codec !in selectedCodecs) return false

        // 3. 码率区间匹配（范围非法时放行，避免无任何结果误导用户）
        if (!isBitrateRangeInvalid) {
            minBitrateKbps?.let { min ->
                if (item.bitrateKbps < min) return false
            }
            maxBitrateKbps?.let { max ->
                if (item.bitrateKbps > max) return false
            }
        }

        return true
    }
}
