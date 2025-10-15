/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.testfixtures

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope

/** Utility object providing a shared dispatcher and factory for test scopes. */
object Fixtures {
    /** Shared dispatcher for tests; using Unconfined to avoid version mismatches. */
    val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    /**
     * Creates a new [TestScope] bound to the shared [dispatcher] so tests can coordinate time advancement
     * without spinning their own dispatcher instances.
     */
    fun testScope(): CoroutineScope = CoroutineScope(dispatcher)
}
