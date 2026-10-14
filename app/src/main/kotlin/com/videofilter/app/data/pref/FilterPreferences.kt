package com.videofilter.app.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.filterDataStore: DataStore<Preferences> by preferencesDataStore(name = "filter_preferences")

/**
 * 码率默认区间。
 *
 * [minKbps] 为 null 表示不设下限；[maxKbps] 为 null 表示不设上限。
 */
data class BitrateDefaults(
    val minKbps: Long?,
    val maxKbps: Long?
)

/**
 * 筛选条件偏好记忆。
 *
 * 记录用户上次使用的码率上下限，作为下次进入应用时的**默认生效值**。
 * 界面上以灰色占位文字呈现（输入框内容本身保持为空），
 * 用户一旦手动输入即以输入值为准。
 */
class FilterPreferences(private val context: Context) {

    val bitrateDefaults: Flow<BitrateDefaults> = context.filterDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { preferences ->
            BitrateDefaults(
                minKbps = if (preferences.contains(KEY_MIN_BITRATE)) {
                    preferences[KEY_MIN_BITRATE]
                } else {
                    DEFAULT_MIN_KBPS
                },
                maxKbps = preferences[KEY_MAX_BITRATE]
            )
        }
        .distinctUntilChanged()

    /**
     * 记忆用户本次填写的码率。
     *
     * 传入 null 表示**保持原值不变**（而不是清除）：
     * 否则用户只是把输入框清空，就会把历史记忆抹掉，与「记忆上次」的意图相悖。
     * 想让下限/上限「不限」，请显式输入 0。
     */
    suspend fun rememberBitrate(minKbps: Long?, maxKbps: Long?) {
        if (minKbps == null && maxKbps == null) return
        context.filterDataStore.edit { preferences ->
            if (minKbps != null) preferences[KEY_MIN_BITRATE] = minKbps
            if (maxKbps != null) preferences[KEY_MAX_BITRATE] = maxKbps
        }
    }

    companion object {
        private val KEY_MIN_BITRATE = longPreferencesKey("min_bitrate_kbps")
        private val KEY_MAX_BITRATE = longPreferencesKey("max_bitrate_kbps")

        /** 首次运行的默认下限：30 Mbps，用于快速筛出高码率视频 */
        const val DEFAULT_MIN_KBPS = 30_000L
    }
}
