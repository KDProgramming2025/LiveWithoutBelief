/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import info.lwb.core.domain.ArticleRepository
import info.lwb.telemetry.Telemetry
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker responsible for refreshing locally cached articles
 * from the remote source via [ArticleRepository].
 *
 * It is scheduled using WorkManager with network + battery constraints to avoid
 * unnecessary background strain. Telemetry trace "sync_refresh" is emitted for
 * performance measurement.
 */
class ArticleSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    /**
     * Hilt entry point used to retrieve dependencies inside the Worker context.
     * WorkManager instantiates workers, so field / constructor injection for
     * [ArticleRepository] is unavailable; this entry point bridges that gap.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        /**
         * Hilt entry point exposing the [ArticleRepository] for the worker when
         * constructor injection is not directly available.
         */
        fun articleRepository(): ArticleRepository
    }

    /**
     * Executes a single refresh pass. Returns [Result.success] on success; on
     * failure it logs telemetry and asks WorkManager to retry respecting backoff.
     */
    override suspend fun doWork(): Result = try {
        Telemetry.startTrace("sync_refresh").use {
            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, AppEntryPoint::class.java)
            val repo = entryPoint.articleRepository()
            repo.refreshArticles()
        }
        Result.success()
    } catch (e: IllegalStateException) {
        Telemetry.recordCaught(e)
        Result.retry()
    }

    /**
     * Companion object acting as the scheduler API for enqueuing the periodic sync.
     */
    companion object Scheduler {
        private const val UNIQUE_NAME = "ArticlePeriodicSync"

        /**
         * Enqueue (or update) a unique periodic sync worker with the given repeat interval.
         *
         * @param context Application context.
         * @param repeatHours Interval in hours between executions (default 6).
         */
        fun schedule(context: Context, repeatHours: Long = 6L) {
            val constraints = Constraints
                .Builder()
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
