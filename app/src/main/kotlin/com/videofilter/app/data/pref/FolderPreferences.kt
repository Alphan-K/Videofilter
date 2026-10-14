package com.videofilter.app.data.pref

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

private val Context.folderDataStore: DataStore<Preferences> by preferencesDataStore(name = "folder_preferences")

/**
 * 目标文件夹偏好记忆管理器。
 *
 * 职责：
 * 1. 持久化用户最后一次选择的输出目录（跨进程存活）；
 * 2. 目录被外部删除 / 无写权限时，自动回退到系统标准公共目录并重建；
 * 3. 以 Flow 形式对外暴露，路径变更时 UI 自动跟随。
 */
class FolderPreferences(private val context: Context) {

    /**
     * 实时观察目标文件夹路径。
     * DataStore 读取可能抛 IOException，此处降级为空偏好而不是让整条流崩溃。
     */
    val targetFolderPath: Flow<String> = context.folderDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { preferences ->
            val savedPath = preferences[KEY_LAST_TARGET_FOLDER]
            resolveUsableDirectory(savedPath).absolutePath
        }
        .distinctUntilChanged()

    /**
     * 持久化保存用户选中的目标路径（仅当目录可写时才落库，避免存入脏路径）
     */
    suspend fun saveTargetFolder(path: String) {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        if (dir.isDirectory && dir.canWrite()) {
            context.folderDataStore.edit { preferences ->
                preferences[KEY_LAST_TARGET_FOLDER] = dir.absolutePath
            }
        }
    }

    companion object {
        private val KEY_LAST_TARGET_FOLDER = stringPreferencesKey("last_target_folder_path")

        /** 外部存储根，例如 /storage/emulated/0 */
        val storageRootPath: String
            get() = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"

        /**
         * 系统级标准兜底目录：/storage/emulated/0/Movies/FilteredVideos
         */
        val defaultTargetDirectory: File
            get() {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val base = if (moviesDir != null) moviesDir else File(Environment.getExternalStorageDirectory(), "Movies")
                return File(base, "FilteredVideos")
            }

        /**
         * 把用户输入解析为绝对路径。
         *
         * 默认按**相对路径**处理（如 `a/v/b` -> `/storage/emulated/0/a/v/b`），
         * 这样在手机输入法下不必敲一长串绝对路径；若用户直接粘了绝对路径也照常支持。
         *
         * 安全约束：规范化后必须仍位于外部存储根之内。
         * 否则 `../../data/data/...` 这类输入会逃逸到系统分区。
         */
        fun resolveUserInput(raw: String): File? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val normalized = trimmed.replace('\\', '/').trimEnd('/')
            if (normalized.isEmpty()) return null

            val candidate = if (normalized.startsWith("/")) {
                File(normalized)
            } else {
                File(storageRootPath, normalized)
            }

            val rootCanonical = runCatching { File(storageRootPath).canonicalPath }.getOrNull()
                ?: return null
            val candidateCanonical = runCatching { candidate.canonicalPath }.getOrNull()
                ?: return null

            val withinRoot = candidateCanonical == rootCanonical ||
                candidateCanonical.startsWith(rootCanonical.trimEnd('/') + "/")
            if (!withinRoot) return null

            return File(candidateCanonical)
        }

        /**
         * 绝对路径 -> 相对展示形式，用于回填输入框
         */
        fun toDisplayPath(absolutePath: String): String {
            val prefix = storageRootPath.trimEnd('/') + "/"
            return if (absolutePath.startsWith(prefix)) absolutePath.removePrefix(prefix) else absolutePath
        }

        /**
         * 校验候选目录是否可用；不可用则回退到默认目录并尽力创建。
         */
        private fun resolveUsableDirectory(savedPath: String?): File {
            val candidate = if (!savedPath.isNullOrBlank()) File(savedPath) else null

            val valid = if (candidate != null) {
                if (!candidate.exists()) candidate.mkdirs()
                if (candidate.isDirectory && candidate.canWrite()) candidate else defaultTargetDirectory
            } else {
                defaultTargetDirectory
            }

            if (!valid.exists()) {
                valid.mkdirs()
            }
            return valid
        }
    }
}
