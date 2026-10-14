package com.videofilter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videofilter.app.ui.theme.LocalAppDimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FilterChipFlowSection(
    title: String,
    items: List<T>,
    selectedItems: Set<T>,
    isAllSelected: Boolean,
    itemLabel: (T) -> String,
    onToggleItem: (T) -> Unit,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalAppDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题栏与全选切换操作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " (${selectedItems.size}/${items.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            TextButton(onClick = onToggleSelectAll) {
                Text(
                    text = if (isAllSelected) "取消全选" else "全选",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 流式 Chip 列表，按屏幕宽度自动断行
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.chipSpacing),
            verticalArrangement = Arrangement.spacedBy(dimens.chipSpacing / 2)
        ) {
            items.forEach { item ->
                val isSelected = item in selectedItems
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleItem(item) },
                    label = {
                        Text(
                            text = itemLabel(item),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Done,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
    }
}
