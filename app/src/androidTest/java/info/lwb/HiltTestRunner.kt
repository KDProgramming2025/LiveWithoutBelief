/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation runner for Hilt tests. It swaps the application with
 * HiltTestApplication so you can use @HiltAndroidTest in instrumentation.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application {
        return super.newApplication(
            cl,
            "dagger.hilt.android.testing.HiltTestApplication",
            context,
        )
    }
}
