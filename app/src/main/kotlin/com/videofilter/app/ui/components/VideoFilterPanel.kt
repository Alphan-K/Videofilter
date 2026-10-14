package com.videofilter.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videofilter.app.model.VideoCodec
import com.videofilter.app.model.VideoContainerFormat
import com.videofilter.app.model.VideoFilterState
import com.videofilter.app.ui.theme.LocalAppDimens

@Composable
fun VideoFilterPanel(
    filterState: VideoFilterState,
    onFilterStateChange: (VideoFilterState) -> Unit,
    minBitratePlaceholder: String,
    maxBitratePlaceholder: String,
    /** 按「套用记忆默认值之后」的生效区间判定，避免界面上不报错却按非法区间放行 */
    isBitrateRangeInvalid: Boolean,
    modifier: Modifier = Modifier
) {
    val dimens = LocalAppDimens.current
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.filterPanelElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 四周留白刻意大于屏幕边距：避免标题/分隔线与选项芯片贴到卡片圆角上
                .padding(
                    horizontal = dimens.filterPanelInnerHorizontal,
                    vertical = dimens.filterPanelInnerVertical
                )
        ) {
            // 控制卡片折叠/展开的头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "筛选",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = "视频高级过滤器",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    // 一键重置全部过滤条件
                    IconButton(
                        onClick = {
                            onFilterStateChange(
                                VideoFilterState(
                                    selectedFormats = VideoContainerFormat.entries.toSet(),
                                    selectedCodecs = VideoCodec.entries.toSet(),
                                    minBitrateInput = "",
                                    maxBitrateInput = ""
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "重置筛选",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 折叠切换
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "展开收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 1. 封装格式全集筛选器
                    FilterChipFlowSection(
                        title = "封装格式 (Container)",
                        items = VideoContainerFormat.entries,
                        selectedItems = filterState.selectedFormats,
                        isAllSelected = filterState.isAllFormatsSelected,
                        itemLabel = { it.displayName },
                        onToggleItem = { format ->
                            val updated = filterState.selectedFormats.toMutableSet().apply {
                                if (contains(format)) remove(format) else add(format)
                            }
                            onFilterStateChange(filterState.copy(selectedFormats = updated))
                        },
                        onToggleSelectAll = {
                            val updated = if (filterState.isAllFormatsSelected) {
                                emptySet()
                            } else {
                                VideoContainerFormat.entries.toSet()
                            }
                            onFilterStateChange(filterState.copy(selectedFormats = updated))
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 2. 视频编码筛选器（重点支持 H.264, H.265, AV1 及其他标准）
                    FilterChipFlowSection(
                        title = "视频编码 (Codec)",
                        items = VideoCodec.entries,
                        selectedItems = filterState.selectedCodecs,
                        isAllSelected = filterState.isAllCodecsSelected,
                        itemLabel = { it.displayName },
                        onToggleItem = { codec ->
                            val updated = filterState.selectedCodecs.toMutableSet().apply {
                                if (contains(codec)) remove(codec) else add(codec)
                            }
                            onFilterStateChange(filterState.copy(selectedCodecs = updated))
                        },
                        onToggleSelectAll = {
                            val updated = if (filterState.isAllCodecsSelected) {
                                emptySet()
                            } else {
                                VideoCodec.entries.toSet()
                            }
                            onFilterStateChange(filterState.copy(selectedCodecs = updated))
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 3. 码率范围限制输入器（框内淡色 kbps，任意单侧有效；灰字为记忆的默认值）
                    BitrateRangeInputField(
                        minBitrateInput = filterState.minBitrateInput,
                        maxBitrateInput = filterState.maxBitrateInput,
                        minPlaceholder = minBitratePlaceholder,
                        maxPlaceholder = maxBitratePlaceholder,
                        isRangeInvalid = isBitrateRangeInvalid,
                        onMinBitrateChanged = { onFilterStateChange(filterState.copy(minBitrateInput = it)) },
                        onMaxBitrateChanged = { onFilterStateChange(filterState.copy(maxBitrateInput = it)) }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
