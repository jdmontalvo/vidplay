package com.vidplay.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vidplay_settings")

enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    DURATION_DESC,
}

enum class LibraryViewMode {
    GRID,
    LIST,
}

data class AppSettings(
    val seekForwardMs: Long = 10_000L,
    val seekBackMs: Long = 10_000L,
    val boostSpeed: Float = 2.0f,
    val maxZoom: Float = 5.0f,
    val autoResume: Boolean = true,
    val keepScreenOn: Boolean = true,
    val backgroundPlay: Boolean = true,
    val gestureSeek: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val vaultLockEnabled: Boolean = true,
    val vaultAutoLockSeconds: Int = 60,
    val hideVaultNotification: Boolean = true,
    val allowVaultPip: Boolean = false,
    val subtitleScale: Float = 1.0f,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val seekForward = longPreferencesKey("seek_forward_ms")
        val seekBack = longPreferencesKey("seek_back_ms")
        val boostSpeed = floatPreferencesKey("boost_speed")
        val maxZoom = floatPreferencesKey("max_zoom")
        val autoResume = booleanPreferencesKey("auto_resume")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val backgroundPlay = booleanPreferencesKey("background_play")
        val gestureSeek = booleanPreferencesKey("gesture_seek")
        val gestureBrightness = booleanPreferencesKey("gesture_brightness")
        val gestureVolume = booleanPreferencesKey("gesture_volume")
        val vaultLock = booleanPreferencesKey("vault_lock")
        val vaultAutoLock = intPreferencesKey("vault_auto_lock_seconds")
        val hideVaultNotification = booleanPreferencesKey("hide_vault_notification")
        val allowVaultPip = booleanPreferencesKey("allow_vault_pip")
        val subtitleScale = floatPreferencesKey("subtitle_scale")
        val sortOrder = stringPreferencesKey("sort_order")
        val viewMode = stringPreferencesKey("view_mode")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            seekForwardMs = prefs[Keys.seekForward] ?: defaults.seekForwardMs,
            seekBackMs = prefs[Keys.seekBack] ?: defaults.seekBackMs,
            boostSpeed = prefs[Keys.boostSpeed] ?: defaults.boostSpeed,
            maxZoom = prefs[Keys.maxZoom] ?: defaults.maxZoom,
            autoResume = prefs[Keys.autoResume] ?: defaults.autoResume,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            backgroundPlay = prefs[Keys.backgroundPlay] ?: defaults.backgroundPlay,
            gestureSeek = prefs[Keys.gestureSeek] ?: defaults.gestureSeek,
            gestureBrightness = prefs[Keys.gestureBrightness] ?: defaults.gestureBrightness,
            gestureVolume = prefs[Keys.gestureVolume] ?: defaults.gestureVolume,
            vaultLockEnabled = prefs[Keys.vaultLock] ?: defaults.vaultLockEnabled,
            vaultAutoLockSeconds = prefs[Keys.vaultAutoLock] ?: defaults.vaultAutoLockSeconds,
            hideVaultNotification = prefs[Keys.hideVaultNotification] ?: defaults.hideVaultNotification,
            allowVaultPip = prefs[Keys.allowVaultPip] ?: defaults.allowVaultPip,
            subtitleScale = prefs[Keys.subtitleScale] ?: defaults.subtitleScale,
            sortOrder = prefs[Keys.sortOrder]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
                ?: defaults.sortOrder,
            viewMode = prefs[Keys.viewMode]?.let { runCatching { LibraryViewMode.valueOf(it) }.getOrNull() }
                ?: defaults.viewMode,
        )
    }

    suspend fun setSeekForward(ms: Long) = edit { it[Keys.seekForward] = ms }
    suspend fun setSeekBack(ms: Long) = edit { it[Keys.seekBack] = ms }
    suspend fun setBoostSpeed(speed: Float) = edit { it[Keys.boostSpeed] = speed }
    suspend fun setMaxZoom(zoom: Float) = edit { it[Keys.maxZoom] = zoom }
    suspend fun setAutoResume(enabled: Boolean) = edit { it[Keys.autoResume] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.keepScreenOn] = enabled }
    suspend fun setBackgroundPlay(enabled: Boolean) = edit { it[Keys.backgroundPlay] = enabled }
    suspend fun setGestureSeek(enabled: Boolean) = edit { it[Keys.gestureSeek] = enabled }
    suspend fun setGestureBrightness(enabled: Boolean) = edit { it[Keys.gestureBrightness] = enabled }
    suspend fun setGestureVolume(enabled: Boolean) = edit { it[Keys.gestureVolume] = enabled }
    suspend fun setVaultLockEnabled(enabled: Boolean) = edit { it[Keys.vaultLock] = enabled }
    suspend fun setVaultAutoLock(seconds: Int) = edit { it[Keys.vaultAutoLock] = seconds }
    suspend fun setHideVaultNotification(enabled: Boolean) = edit { it[Keys.hideVaultNotification] = enabled }
    suspend fun setAllowVaultPip(enabled: Boolean) = edit { it[Keys.allowVaultPip] = enabled }
    suspend fun setSubtitleScale(scale: Float) = edit { it[Keys.subtitleScale] = scale }
    suspend fun setSortOrder(order: SortOrder) = edit { it[Keys.sortOrder] = order.name }
    suspend fun setViewMode(mode: LibraryViewMode) = edit { it[Keys.viewMode] = mode.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
