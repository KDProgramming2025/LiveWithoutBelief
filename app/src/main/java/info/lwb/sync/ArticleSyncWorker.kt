/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import info.lwb.core.domain.ArticleRepository
import java.util.concurrent.TimeUnit
import info.lwb.telemetry.Telemetry

class ArticleSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun articleRepository(): ArticleRepository
    }

    override suspend fun doWork(): Result = try {
    Telemetry.startTrace("sync_refresh").use { _ ->
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, AppEntryPoint::class.java)
        val repo = entryPoint.articleRepository()
        repo.refreshArticles()
    }
    Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object Scheduler {
        private const val UNIQUE_NAME = "ArticlePeriodicSync"

        fun schedule(context: Context, repeatHours: Long = 6L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<ArticleSyncWorker>(repeatHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
