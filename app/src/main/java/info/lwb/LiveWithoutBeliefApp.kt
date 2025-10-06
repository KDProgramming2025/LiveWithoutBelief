/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb

// No custom WorkManager factory needed; worker uses Hilt EntryPoint
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import info.lwb.telemetry.Telemetry
import info.lwb.core.common.log.Logger
import info.lwb.app.logging.AndroidLogger

/**
 * Application entry point initializing telemetry and scheduling periodic background sync.
 *
 * Responsibilities:
 *  - Initialize [Telemetry] early for structured event logging.
 *  - Emit an app_start event with version metadata.
 *  - Schedule the recurring [ArticleSyncWorker] to keep local content fresh.
 */
@HiltAndroidApp
class LiveWithoutBeliefApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.install(AndroidLogger(tag = "LWB"))
        Telemetry.init(this)
        Telemetry.logEvent("app_start", mapOf("ver" to BuildConfig.VERSION_NAME))
    }
}
