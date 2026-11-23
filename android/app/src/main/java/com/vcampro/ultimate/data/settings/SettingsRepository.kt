package com.vcampro.ultimate.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.vcamDataStore: DataStore<Preferences> by preferencesDataStore(name = "vcam_settings")

/**
 * Everything the app remembers between launches. Nothing here leaves the device.
 */
data class VcamSettings(
    val language: String = "vi",
    val themeMode: String = "dark",
    val dynamicColor: Boolean = false,
    val performanceMode: String = "balanced",
    val onboardingCompleted: Boolean = false,
    val appLockEnabled: Boolean = false,
    val appLockPinHash: String = "",
    val gridEnabled: Boolean = true,
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "VCAM PRO",
    val cameraLensFacing: String = "back",
    val videoQuality: String = "FHD",
    val jpegQuality: Int = 95,
    val defaultFps: Int = 30,
    val lastUpdateCheck: Long = 0L
) {
    val isEnglish: Boolean get() = language.equals("en", ignoreCase = true)
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val language = stringPreferencesKey("language")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val performanceMode = stringPreferencesKey("performance_mode")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
        val appLockPinHash = stringPreferencesKey("app_lock_pin_hash")
        val gridEnabled = booleanPreferencesKey("grid_enabled")
        val watermarkEnabled = booleanPreferencesKey("watermark_enabled")
        val watermarkText = stringPreferencesKey("watermark_text")
        val cameraLensFacing = stringPreferencesKey("camera_lens_facing")
        val videoQuality = stringPreferencesKey("video_quality")
        val jpegQuality = intPreferencesKey("jpeg_quality")
        val defaultFps = intPreferencesKey("default_fps")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
    }

    val settings: Flow<VcamSettings> = context.vcamDataStore.data
        // A damaged preferences file must never take the app down at launch.
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
        val defaults = VcamSettings()
        VcamSettings(
            language = preferences[Keys.language] ?: defaults.language,
            themeMode = preferences[Keys.themeMode] ?: defaults.themeMode,
            dynamicColor = preferences[Keys.dynamicColor] ?: defaults.dynamicColor,
            performanceMode = preferences[Keys.performanceMode] ?: defaults.performanceMode,
            onboardingCompleted = preferences[Keys.onboardingCompleted] ?: defaults.onboardingCompleted,
            appLockEnabled = preferences[Keys.appLockEnabled] ?: defaults.appLockEnabled,
            appLockPinHash = preferences[Keys.appLockPinHash] ?: defaults.appLockPinHash,
            gridEnabled = preferences[Keys.gridEnabled] ?: defaults.gridEnabled,
            watermarkEnabled = preferences[Keys.watermarkEnabled] ?: defaults.watermarkEnabled,
            watermarkText = preferences[Keys.watermarkText] ?: defaults.watermarkText,
            cameraLensFacing = preferences[Keys.cameraLensFacing] ?: defaults.cameraLensFacing,
            videoQuality = preferences[Keys.videoQuality] ?: defaults.videoQuality,
            jpegQuality = preferences[Keys.jpegQuality] ?: defaults.jpegQuality,
            defaultFps = preferences[Keys.defaultFps] ?: defaults.defaultFps,
            lastUpdateCheck = preferences[Keys.lastUpdateCheck] ?: defaults.lastUpdateCheck
        )
    }

    suspend fun setLanguage(code: String) = edit { it[Keys.language] = code }

    suspend fun setThemeMode(mode: String) = edit { it[Keys.themeMode] = mode }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }

    suspend fun setPerformanceMode(mode: String) = edit { it[Keys.performanceMode] = mode }

    suspend fun setOnboardingCompleted(completed: Boolean) = edit { it[Keys.onboardingCompleted] = completed }

    suspend fun setAppLockEnabled(enabled: Boolean) = edit { it[Keys.appLockEnabled] = enabled }

    suspend fun setAppLockPinHash(hash: String) = edit { it[Keys.appLockPinHash] = hash }

    suspend fun setGridEnabled(enabled: Boolean) = edit { it[Keys.gridEnabled] = enabled }

    suspend fun setWatermarkEnabled(enabled: Boolean) = edit { it[Keys.watermarkEnabled] = enabled }

    suspend fun setWatermarkText(text: String) = edit { it[Keys.watermarkText] = text }

    suspend fun setCameraLensFacing(facing: String) = edit { it[Keys.cameraLensFacing] = facing }

    suspend fun setVideoQuality(quality: String) = edit { it[Keys.videoQuality] = quality }

    suspend fun setJpegQuality(quality: Int) = edit { it[Keys.jpegQuality] = quality.coerceIn(60, 100) }

    suspend fun setDefaultFps(fps: Int) = edit { it[Keys.defaultFps] = fps.coerceIn(15, 60) }

    suspend fun setLastUpdateCheck(timestamp: Long) = edit { it[Keys.lastUpdateCheck] = timestamp }

    suspend fun resetAll() {
        context.vcamDataStore.edit { preferences -> preferences.clear() }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.vcamDataStore.edit(block)
    }
}
