package com.videofilter.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.videofilter.app.permission.StoragePermissionHelper

@Composable
fun StoragePermissionGate(
    modifier: Modifier = Modifier,
    onPermissionGrantedContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(StoragePermissionHelper.hasAllFilesAccess(context))
    }

    // API 26~29 的存储权限是危险权限，必须走运行时申请
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // 核心生命周期监听：当用户在系统设置开启权限并返回 App 时，即时触发刷新
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = StoragePermissionHelper.hasAllFilesAccess(context)
    }

    if (hasPermission) {
        onPermissionGrantedContent()
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderSpecial,
                        contentDescription = "存储权限",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "需要设备全盘访问权限",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "为了深入检测手机中所有的视频封装（如 MKV、FLV、TS）并解析 H.264/H.265/AV1 编解码器与真实码率，程序需要系统级的所有文件管理权限。"
                        } else {
                            "为了扫描手机中的视频并解析编解码器与真实码率，需要授予存储读取权限。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            val legacy = StoragePermissionHelper.legacyRuntimePermission()
                            if (legacy != null) {
                                legacyPermissionLauncher.launch(legacy)
                            } else {
                                StoragePermissionHelper.requestAllFilesAccess(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "前往系统授权",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
