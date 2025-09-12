/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb

// No custom WorkManager factory needed; worker uses Hilt EntryPoint
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import info.lwb.sync.ArticleSyncWorker
import info.lwb.telemetry.Telemetry

@HiltAndroidApp
class LiveWithoutBeliefApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this)
        Telemetry.logEvent("app_start", mapOf("ver" to BuildConfig.VERSION_NAME))
        // Schedule periodic article sync (every 6 hours by default)
        ArticleSyncWorker.schedule(this)
    }
}
