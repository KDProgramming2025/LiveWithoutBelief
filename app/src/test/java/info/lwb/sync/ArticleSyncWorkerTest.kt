/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
class ArticleSyncWorkerTest {
    @Test
    fun schedule_doesNotCrash() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration
            .Builder()
            .setExecutor(Executors.newSingleThreadExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, config)
        ArticleSyncWorker.schedule(ctx, repeatHours = 6)
    }
}
