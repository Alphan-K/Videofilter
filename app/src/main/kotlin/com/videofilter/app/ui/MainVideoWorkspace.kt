package com.videofilter.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videofilter.app.scanner.ScanMode
import com.videofilter.app.scanner.ScanPhase
import com.videofilter.app.scanner.VideoFolder
import com.videofilter.app.ui.components.AutoMovePromptDialog
import com.videofilter.app.ui.components.AutoMoveSettingsDialog
import com.videofilter.app.ui.components.MoveBottomBar
import com.videofilter.app.ui.components.MoveProgressDialog
import com.videofilter.app.ui.components.VideoFilterPanel
import com.videofilter.app.ui.components.VideoItemCard
import com.videofilter.app.ui.components.WhitelistPanel
import com.videofilter.app.ui.theme.LocalAppDimens
import com.videofilter.app.ui.viewmodel.FilterViewModel
import com.videofilter.app.ui.viewmodel.ScanUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainVideoWorkspace(
    modifier: Modifier = Modifier,
    viewModel: FilterViewModel = viewModel()
) {
    val dimens = LocalAppDimens.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val rawVideos by viewModel.rawVideos.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val totalScanned by viewModel.totalScannedCount.collectAsStateWithLifecycle()

    val activeFolder by viewModel.activeFolder.collectAsStateWithLifecycle()
    val folderCounts by viewModel.folderCounts.collectAsStateWithLifecycle()
    val folderScanTimes by viewModel.folderScanTimes.collectAsStateWithLifecycle()
    val isActiveFolderStale by viewModel.isActiveFolderStale.collectAsStateWithLifecycle()

    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
    val targetFolderPath by viewModel.targetFolderPath.collectAsStateWithLifecycle()
    val moveProgressEvent by viewModel.moveProgressEvent.collectAsStateWithLifecycle()

    val whitelistVideos by viewModel.whitelistVideos.collectAsStateWithLifecycle()
    val isWhitelistVisible by viewModel.isWhitelistVisible.collectAsStateWithLifecycle()
    val bitrateDefaults by viewModel.bitrateDefaults.collectAsStateWithLifecycle()
    val isBitrateRangeInvalid by viewModel.isBitrateRangeInvalid.collectAsStateWithLifecycle()

    val autoMoveFolders by viewModel.autoMoveFolders.collectAsStateWithLifecycle()
    val autoMovePrompt by viewModel.autoMovePrompt.collectAsStateWithLifecycle()
    val isAutoMoveSettingsVisible by viewModel.isAutoMoveSettingsVisible.collectAsStateWithLifecycle()

    val isScanning = scanState is ScanUiState.Scanning
    val scanLabel = if (activeFolder.isScannable) "扫描 ${activeFolder.displayName}" else "扫描全盘"
    val activeScannedAt = folderScanTimes[activeFolder]
    // 灰字占位：记忆的默认码率；上限为空时展示「不限」
    val minPlaceholder = bitrateDefaults.minKbps?.toString() ?: "不限"
    val maxPlaceholder = bitrateDefaults.maxKbps?.toString() ?: "不限"
    // 生效区间：输入框有值以输入为准，为空则沿用记忆的默认值
    val effectiveMinKbps = filterState.minBitrateInput.toLongOrNull() ?: bitrateDefaults.minKbps
    val effectiveMaxKbps = filterState.maxBitrateInput.toLongOrNull() ?: bitrateDefaults.maxKbps

    BackHandler(enabled = isWhitelistVisible) { viewModel.hideWhitelist() }
    BackHandler(enabled = isAutoMoveSettingsVisible) { viewModel.hideAutoMoveSettings() }
    BackHandler(enabled = isSelectionMode && !isWhitelistVisible) { viewModel.exitSelectionMode() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.screenHorizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (isSelectionMode) {
                // ---------------- 多选顶栏 ----------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimens.minTouchTargetSize),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::exitSelectionMode) {
                            Icon(Icons.Rounded.Close, contentDescription = "退出选择")
                        }
                        Text(
                            text = "已选 ${selectedPaths.size} 项",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Row {
                        TextButton(onClick = viewModel::selectAllFiltered) { Text("全选") }
                        TextButton(onClick = viewModel::clearSelection) { Text("清空") }
                    }
                }
            } else {
                // ---------------- 常规顶栏 ----------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "全盘视频筛选器",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            // 同时给出「筛选后 / 目录内总数」，否则只看总数会误以为筛选没生效
                            text = "${activeFolder.displayName} " +
                                "${filteredVideos.size}/${rawVideos.size} · " +
                                "全库 $totalScanned · 白名单 ${whitelistVideos.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 扫描后自动询问移动的按目录开关
                    IconButton(onClick = viewModel::showAutoMoveSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "自动询问设置",
                            tint = if (autoMoveFolders.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    // 返回顶部（置于白名单盾牌标左侧）
                    IconButton(
                        onClick = {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerticalAlignTop,
                            contentDescription = "返回顶部",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 白名单查看入口
                    IconButton(onClick = viewModel::showWhitelist) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "查看白名单",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                // ---------------- 目录入口：数量 + 上次扫描时间 ----------------
                // 用 FlowRow 自动换行而不是横向滚动：确保 5 个目录入口全部可见，
                // 否则靠右的「Download / 其他」会被屏幕边缘裁掉，用户也不知道可以横向滑动。
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    viewModel.folders.forEach { folder ->
                        FolderEntryChip(
                            folder = folder,
                            count = folderCounts[folder] ?: 0,
                            lastScannedAt = folderScanTimes[folder],
                            isActive = folder == activeFolder,
                            onClick = { viewModel.setActiveFolder(folder) }
                        )
                    }
                }

                // ---------------- 扫描动作 ----------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isScanning) {
                        Button(
                            onClick = { viewModel.cancelScan() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(18.dp)
                            )
                            Text(text = "停止扫描")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (activeFolder.isScannable) {
                                    viewModel.scanActiveFolder(ScanMode.INCREMENTAL)
                                } else {
                                    viewModel.scanWholeDisk(ScanMode.INCREMENTAL)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(18.dp)
                            )
                            Text(text = scanLabel)
                        }

                        OutlinedButton(
                            onClick = {
                                if (activeFolder.isScannable) {
                                    viewModel.scanActiveFolder(ScanMode.FULL)
                                } else {
                                    viewModel.scanWholeDisk(ScanMode.FULL)
                                }
                            },
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(18.dp)
                            )
                            Text(text = "全量")
                        }
                    }
                }

                // ---------------- 数据新鲜度提示 ----------------
                if (activeScannedAt == null || isActiveFolderStale) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (activeScannedAt == null) {
                                "${activeFolder.displayName} 尚未扫描过，点「$scanLabel」获取数据"
                            } else {
                                "${activeFolder.displayName} 的数据来自 " +
                                    "${formatScanTime(activeScannedAt)}，之后扫描过其他目录，可能不是最新"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            // ---------------- 扫描进度 ----------------
            AnimatedVisibility(visible = isScanning, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    val scanning = scanState as? ScanUiState.Scanning
                    if (scanning != null) {
                        val phaseLabel = when (scanning.phase) {
                            ScanPhase.TRAVERSING -> "遍历中（仅 stat 比对）"
                            ScanPhase.PARSING -> "解析变更文件"
                        }
                        Text(
                            text = "[${scanning.scope.displayName} · $phaseLabel] 已处理 ${scanning.processedFiles}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = scanning.currentPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ---------------- 扫描结果 ----------------
            val success = scanState as? ScanUiState.Success
            AnimatedVisibility(visible = success != null, enter = fadeIn(), exit = fadeOut()) {
                if (success != null) {
                    val s = success.stats
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { viewModel.resetScanState() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "${s.scope.displayName}扫描完成 · ${s.elapsedMs} ms",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "命中 ${s.totalOnDisk} · 跳过未变更 ${s.unchanged} · " +
                                    "解析 ${s.toParse} · 成功 ${s.parsedOk} · 清理 ${s.deleted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            val error = scanState as? ScanUiState.Error
            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .clickable { viewModel.resetScanState() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "扫描失败：${error.message}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---------------- 视频列表（仅当前目录） ----------------
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (isSelectionMode) 150.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(dimens.gridSpacing)
            ) {
                item(key = "filter_panel_header") {
                    VideoFilterPanel(
                        filterState = filterState,
                        onFilterStateChange = viewModel::updateFilterState,
                        minBitratePlaceholder = minPlaceholder,
                        maxBitratePlaceholder = maxPlaceholder,
                        isBitrateRangeInvalid = isBitrateRangeInvalid
                    )
                }

                if (filteredVideos.isEmpty()) {
                    item(key = "empty_placeholder") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = when {
                                        rawVideos.isEmpty() && activeScannedAt == null ->
                                            "${activeFolder.displayName} 还没有数据，先扫描一次"
                                        rawVideos.isEmpty() ->
                                            "${activeFolder.displayName} 下没有视频"
                                        // 有视频但被筛掉了：明确指出是码率条件拦的，否则用户会以为扫描失败
                                        effectiveMinKbps != null && effectiveMinKbps > 0 ->
                                            "本目录 ${rawVideos.size} 个视频均低于当前默认下限 " +
                                                "${effectiveMinKbps} kbps，把输入框改成 0 可查看全部"
                                        effectiveMaxKbps != null ->
                                            "本目录 ${rawVideos.size} 个视频均高于当前上限 " +
                                                "${effectiveMaxKbps} kbps"
                                        else -> "没有符合当前筛选条件的视频"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(items = filteredVideos, key = { it.id }) { videoItem ->
                        VideoItemCard(
                            video = videoItem,
                            isSelected = selectedPaths.contains(videoItem.path),
                            isSelectionMode = isSelectionMode,
                            onCardClick = {
                                if (isSelectionMode) viewModel.toggleSelection(videoItem.path)
                            },
                            onCardLongClick = {
                                viewModel.enterSelectionMode(videoItem.path)
                            }
                        )
                    }
                }
            }
        }

        // ---------------- 底部悬浮操作栏 ----------------
        AnimatedVisibility(
            visible = isSelectionMode && !isWhitelistVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MoveBottomBar(
                selectedCount = selectedPaths.size,
                targetPath = targetFolderPath,
                onTargetFolderChange = viewModel::updateTargetFolder,
                onAddToWhitelist = viewModel::addSelectedToWhitelist,
                onExecuteMove = viewModel::executeMoveSelected
            )
        }

        // ---------------- 白名单面板 ----------------
        AnimatedVisibility(visible = isWhitelistVisible, enter = fadeIn(), exit = fadeOut()) {
            WhitelistPanel(
                entries = whitelistVideos,
                onClose = viewModel::hideWhitelist,
                onRemove = viewModel::removeFromWhitelist
            )
        }

        // ---------------- 移动进度 / 结果弹窗 ----------------
        moveProgressEvent?.let { event ->
            MoveProgressDialog(event = event, onDismiss = viewModel::dismissMoveDialog)
        }

        // ---------------- 扫描完成后的自动移动询问 ----------------
        autoMovePrompt?.let { prompt ->
            AutoMovePromptDialog(
                folder = prompt.folder,
                candidates = prompt.candidates,
                targetPath = prompt.targetPath,
                isFolderPromptEnabled = prompt.folder in autoMoveFolders,
                onToggleFolderPrompt = { enabled ->
                    viewModel.setAutoMoveEnabled(prompt.folder, enabled)
                },
                onConfirm = viewModel::confirmAutoMove,
                onDismiss = viewModel::dismissAutoMove
            )
        }

        // ---------------- 自动询问开关设置 ----------------
        if (isAutoMoveSettingsVisible) {
            AutoMoveSettingsDialog(
                enabledFolders = autoMoveFolders,
                onToggle = viewModel::setAutoMoveEnabled,
                onDismiss = viewModel::hideAutoMoveSettings
            )
        }
    }
}

/**
 * 目录入口卡片：显示目录名、记录数与上次扫描时间。
 */
@Composable
private fun FolderEntryChip(
    folder: VideoFolder,
    count: Int,
    lastScannedAt: Long?,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isActive) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = folder.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
            Text(
                text = "$count 个 · ${lastScannedAt?.let { formatScanTime(it) } ?: "未扫描"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                maxLines = 1
            )
        }
    }
}

private val SCAN_TIME_FORMATTER = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun formatScanTime(timestamp: Long): String =
    SCAN_TIME_FORMATTER.format(Date(timestamp))
