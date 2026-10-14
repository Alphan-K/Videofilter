package com.videofilter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.videofilter.app.ui.theme.LocalAppDimens

/** 中间分隔符的固定宽度，用于让上下两行左右对齐 */
private val SeparatorWidth = 32.dp

/**
 * 码率范围输入器。
 *
 * 设计要点：**输入框内不放 label**。
 * Material3 的 OutlinedTextField 在「空值 + 未聚焦」时会把 label 压在框内，
 * 此时 placeholder 根本不会被绘制 —— 记忆值就没法以灰字呈现。
 * 因此把「最低码率 / 最高码率」提到框上方作为独立标签，
 * 框内留给灰色占位文字展示上次记忆的默认值。
 */
@Composable
fun BitrateRangeInputField(
    minBitrateInput: String,
    maxBitrateInput: String,
    /** 灰色占位：记忆的下限（输入框为空时即以此为准参与筛选） */
    minPlaceholder: String,
    /** 灰色占位：记忆的上限 */
    maxPlaceholder: String,
    isRangeInvalid: Boolean,
    onMinBitrateChanged: (String) -> Unit,
    onMaxBitrateChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalAppDimens.current
    val focusManager = LocalFocusManager.current

    // 占位文字用灰阶呈现，与真正的输入文本（onSurface）区分开
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "码率范围限制",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 输入框上方的角色标签（与下方两个输入框左右对齐）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing)
        ) {
            Text(
                text = "最低码率",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(SeparatorWidth))
            Text(
                text = "最高码率",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 下限输入框
            OutlinedTextField(
                value = minBitrateInput,
                onValueChange = { input ->
                    // 过滤非数字字符，且限制最大输入位数为 9 位以防数值溢出
                    val sanitized = input.filter { it.isDigit() }.take(9)
                    onMinBitrateChanged(sanitized)
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(text = minPlaceholder, color = placeholderColor)
                },
                suffix = {
                    Text(
                        text = "kbps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                },
                isError = isRangeInvalid,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Box(
                modifier = Modifier.width(SeparatorWidth),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 上限输入框
            OutlinedTextField(
                value = maxBitrateInput,
                onValueChange = { input ->
                    val sanitized = input.filter { it.isDigit() }.take(9)
                    onMaxBitrateChanged(sanitized)
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(text = maxPlaceholder, color = placeholderColor)
                },
                suffix = {
                    Text(
                        text = "kbps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                },
                isError = isRangeInvalid,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }

        // 生效值说明 + 错误提示区
        if (isRangeInvalid) {
            Text(
                text = "下限码率不能大于上限码率",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        } else {
            val effective = buildString {
                append("生效：")
                append(if (minPlaceholder == "0") "不限" else "≥ $minPlaceholder kbps")
                append("，")
                append(if (maxPlaceholder == "0") "不限" else "≤ $maxPlaceholder kbps")
            }
            Text(
                text = effective,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            Text(
                text = "框内灰字为上次记忆的默认值，留空即按此筛选；填 0 表示不限",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}
