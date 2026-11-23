package com.vcampro.ultimate.core.di

import android.content.Context
import com.vcampro.ultimate.data.db.AppDatabase
import com.vcampro.ultimate.data.db.MediaDao
import com.vcampro.ultimate.data.device.DeviceCapabilityAnalyzer
import com.vcampro.ultimate.data.media.MediaRepository
import com.vcampro.ultimate.data.media.PhotoEditorEngine
import com.vcampro.ultimate.data.media.VideoEditorEngine
import com.vcampro.ultimate.data.media.VisionAnalyzer
import com.vcampro.ultimate.data.security.SecurityRepository
import com.vcampro.ultimate.data.settings.SettingsRepository
import com.vcampro.ultimate.data.update.UpdateChecker

/**
 * Hand written dependency container.
 *
 * A tiny object graph is enough for a single module app and it keeps the build
 * free of annotation processors beyond Room's KSP step, which shortens builds on
 * low end developer machines and in CI.
 */
object AppGraph {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context
        get() {
            check(::appContext.isInitialized) { "AppGraph.init() must be called from Application.onCreate()" }
            return appContext
        }

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    val mediaDao: MediaDao by lazy { database.mediaDao() }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val media: MediaRepository by lazy { MediaRepository(appContext, mediaDao) }

    val device: DeviceCapabilityAnalyzer by lazy { DeviceCapabilityAnalyzer(appContext) }

    val photoEditor: PhotoEditorEngine by lazy { PhotoEditorEngine() }

    val videoEditor: VideoEditorEngine by lazy { VideoEditorEngine(appContext) }

    val vision: VisionAnalyzer by lazy { VisionAnalyzer() }

    val security: SecurityRepository by lazy { SecurityRepository(appContext, settings) }

    val updater: UpdateChecker by lazy { UpdateChecker(appContext) }
}
