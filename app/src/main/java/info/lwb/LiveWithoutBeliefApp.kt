/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb

import android.app.Application
// No custom WorkManager factory needed; worker uses Hilt EntryPoint
import info.lwb.sync.ArticleSyncWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LiveWithoutBeliefApp : Application() {
	override fun onCreate() {
		super.onCreate()
		// Schedule periodic article sync (every 6 hours by default)
		ArticleSyncWorker.schedule(this)
	}
}
