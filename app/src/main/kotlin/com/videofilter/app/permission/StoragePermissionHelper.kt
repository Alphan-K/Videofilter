package com.videofilter.app.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

object StoragePermissionHelper {

    private const val TAG = "StoragePermissionHelper"

    /**
     * 严格检查当前是否具备全盘扫描能力。
     *
     * - API 30+（Android 11 起）：需要 MANAGE_EXTERNAL_STORAGE（所有文件访问权限）
     * - API 26~29（Android 8/9）：该权限不存在，改走 READ_EXTERNAL_STORAGE **运行时权限**
     *
     * 注意：低版本上 READ_EXTERNAL_STORAGE 属于危险权限，必须在运行时申请；
     * 仅写入 Manifest 是不够的，否则扫描会静默返回空结果。
     */
    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** API 26~29 需要动态申请的权限；API 30+ 返回 null（走系统设置页） */
    fun legacyRuntimePermission(): String? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            null
        }

    /**
     * 三级降级安全跳转系统授权页面（仅适用于 API 30+）
     */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        // 尝试 1：专属 App 授权直达页
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Step 1 failed: ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION with URI", e)
        }

        // 尝试 2：全局所有文件访问权限列表
        try {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Step 2 failed: ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION generic", e)
        }

        // 尝试 3：终极兜底，跳转到应用系统设置详情页
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Step 3 failed: ACTION_APPLICATION_DETAILS_SETTINGS fallback", e)
        }
    }
}
