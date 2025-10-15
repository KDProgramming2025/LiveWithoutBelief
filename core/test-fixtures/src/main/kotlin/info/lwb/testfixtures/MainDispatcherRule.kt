/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.testfixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 [TestWatcher] rule that sets a [StandardTestDispatcher] as the main dispatcher
 * for tests that need to control coroutine timing. It installs a fresh [TestCoroutineScheduler]
 * per test via the configurable [scheduler] parameter and resets the main dispatcher afterward.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    /** Scheduler driving the virtual time for coroutines launched on [dispatcher]. */
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
) : TestWatcher() {
    /** The test dispatcher installed as Dispatchers.Main for the duration of the test. */
    val dispatcher = StandardTestDispatcher(scheduler)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
