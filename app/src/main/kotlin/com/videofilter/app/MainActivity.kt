package com.videofilter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.videofilter.app.ui.MainVideoWorkspace
import com.videofilter.app.ui.components.StoragePermissionGate
import com.videofilter.app.ui.theme.VideoFilterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 16 核心要求：在 setContent 之前显式调用边缘到边缘
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            VideoFilterTheme {
                // 安全区域隔离，杜绝 20:9 屏挖孔与手势横条遮挡
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    // 权限看门狗拦截
                    StoragePermissionGate(modifier = Modifier.fillMaxSize()) {
                        // 权限通过后挂载主内容区
                        MainVideoWorkspace()
                    }
                }
            }
        }
    }
}
