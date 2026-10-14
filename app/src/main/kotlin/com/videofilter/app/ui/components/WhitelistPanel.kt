package com.videofilter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videofilter.app.model.VideoItem
import com.videofilter.app.ui.theme.LocalAppDimens
import com.videofilter.app.ui.viewmodel.WhitelistEntry
import java.util.Locale

/**
 * 白名单查看面板（全屏覆盖层）。
 *
 * 白名单是「已审核通过、不再需要复核」的视频集合，因此从主列表移出，
 * 在这里独立查看与移除。
 */
@Composable
fun WhitelistPanel(
    entries: List<WhitelistEntry>,
    onClose: () -> Unit,
    onRemove: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalAppDimens.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.screenHorizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.minTouchTargetSize),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭白名单"
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "白名单 (${entries.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (entries.isNotEmpty()) {
                    TextButton(onClick = { onRemove(entries.map { it.item.path }) }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(text = "全部移出", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            Text(
                text = "白名单中的视频不会再出现在主审核列表，记录永久保留。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "白名单还是空的",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "在主列表长按视频后点「白名单」即可加入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(dimens.gridSpacing)
                ) {
                    items(items = entries, key = { it.item.id }) { entry ->
                        WhitelistItemCard(
                            video = entry.item,
                            existsOnDisk = entry.existsOnDisk,
                            onRemove = { onRemove(listOf(entry.item.path)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhitelistItemCard(
    video: VideoItem,
    existsOnDisk: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalAppDimens.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardVerticalPadding)
        ) {
            Text(
                text = video.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = video.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )

            // 源文件已不在磁盘上（被删除或被外部移走）：明确告知，避免用户误以为仍然有效
            if (!existsOnDisk) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = " 源文件已不存在（被删除或被移走），建议移出白名单",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (video.bitrateKbps > 0) "${video.bitrateKbps} kbps" else "未知码率",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(text = video.codec.displayName) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    border = null
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(text = video.containerFormat.displayName) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    border = null
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatSize(video.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRemove) {
                    Text(text = "移出白名单")
                }
            }
        }
    }
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val mb = sizeBytes / (1024f * 1024f)
    return if (mb >= 1024f) {
        String.format(Locale.ROOT, "%.2f GB", mb / 1024f)
    } else {
        String.format(Locale.ROOT, "%.1f MB", mb)
    }
}
