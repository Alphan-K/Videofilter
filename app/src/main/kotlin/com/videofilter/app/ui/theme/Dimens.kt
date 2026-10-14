package com.videofilter.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppDimensions(
    val screenHorizontalPadding: Dp,
    val cardVerticalPadding: Dp,
    val chipSpacing: Dp,
    val gridSpacing: Dp,
    val minTouchTargetSize: Dp,
    val headerIconSize: Dp,
    val cardCornerRadius: Dp,
    val filterPanelElevation: Dp,
    val videoCardThumbnailHeight: Dp,
    /** 过滤器面板内框左右留白（需大于屏幕边距，避免选项芯片贴到卡片边缘） */
    val filterPanelInnerHorizontal: Dp,
    /** 过滤器面板内框上下留白（避免标题与分隔线/芯片挤在一起） */
    val filterPanelInnerVertical: Dp,
    val isUltraTallScreen: Boolean // 标识是否为 20:9 或更高比例的修长屏
)

// 针对 20:9 高清比例（3200x1440, 2400x1080 等）专属调优的尺寸集
private val TallAspectRatioDimensions = AppDimensions(
    screenHorizontalPadding = 20.dp,
    cardVerticalPadding = 14.dp,
    chipSpacing = 10.dp,
    gridSpacing = 16.dp,
    minTouchTargetSize = 52.dp, // 大屏/高像素密度下扩大触控目标，避免误触
    headerIconSize = 28.dp,
    cardCornerRadius = 24.dp, // 匹配 2K 旗舰机的大 R 弧度屏幕圆角
    filterPanelElevation = 6.dp,
    videoCardThumbnailHeight = 110.dp,
    filterPanelInnerHorizontal = 36.dp,
    filterPanelInnerVertical = 32.dp,
    isUltraTallScreen = true
)

// 标准屏比例（如传统 16:9, 18:9）兜底尺寸集
private val StandardDimensions = AppDimensions(
    screenHorizontalPadding = 16.dp,
    cardVerticalPadding = 10.dp,
    chipSpacing = 8.dp,
    gridSpacing = 12.dp,
    minTouchTargetSize = 48.dp,
    headerIconSize = 24.dp,
    cardCornerRadius = 16.dp,
    filterPanelElevation = 4.dp,
    videoCardThumbnailHeight = 90.dp,
    filterPanelInnerHorizontal = 28.dp,
    filterPanelInnerVertical = 26.dp,
    isUltraTallScreen = false
)

@Composable
fun rememberAppDimensions(): AppDimensions {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenWidth = configuration.screenWidthDp.toFloat()

    // 计算实际纵横比
    val aspectRatio = if (screenWidth > 0) screenHeight / screenWidth else 1.77f

    // 20:9 的比例约为 2.22。若纵横比 >= 2.15，则判定为包含 3200x1440 / 2400x1080 在内的细长高分屏
    val is20to9Family = aspectRatio >= 2.15f

    return if (is20to9Family) {
        TallAspectRatioDimensions
    } else {
        StandardDimensions
    }
}

val LocalAppDimens = staticCompositionLocalOf { StandardDimensions }
