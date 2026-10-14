package com.videofilter.app.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.videofilter.app.scanner.VideoFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.autoMoveDataStore: DataStore<Preferences> by preferencesDataStore(name = "auto_move_preferences")

/**
 * 「扫描完成后自动询问是否移动」的按目录开关。
 *
 * 每个归档分组（DCIM / Pictures / Movies / Download / 其他）都有独立的开关，互不影响：
 * 例如可以只在扫 DCIM 后询问、扫 Pictures 后静默。
 *
 * 首次运行的默认值由 [VideoFolder.defaultAutoMoveEnabled] 决定（仅 DCIM 为开）。
 */
class AutoMovePreferences(private val context: Context) {

    /** 当前已开启自动询问的目录集合 */
    val enabledFolders: Flow<Set<VideoFolder>> = context.autoMoveDataStore.data
        .catch { e ->
            // 读取失败降级为「未设置」，让每条记录的默认值兜底，而不是让整条流崩溃
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { preferences ->
            VideoFolder.entries.filterTo(mutableSetOf()) { folder ->
                preferences[keyOf(folder)] ?: folder.defaultAutoMoveEnabled
            }
        }
        .distinctUntilChanged()

    suspend fun setEnabled(folder: VideoFolder, enabled: Boolean) {
        context.autoMoveDataStore.edit { preferences ->
            preferences[keyOf(folder)] = enabled
        }
    }

    private companion object {
        fun keyOf(folder: VideoFolder) = booleanPreferencesKey("auto_move_${folder.name}")
    }
}
