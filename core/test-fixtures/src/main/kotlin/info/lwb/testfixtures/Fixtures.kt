/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.testfixtures

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** Utility object providing a shared dispatcher and factory for test scopes. */
object Fixtures {
    /** Shared [StandardTestDispatcher] for deterministic virtual-time unit tests. */
    val dispatcher: StandardTestDispatcher = StandardTestDispatcher()

    /**
     * Creates a new [TestScope] bound to the shared [dispatcher] so tests can coordinate time advancement
     * without spinning their own dispatcher instances.
     */
    fun testScope(): TestScope = TestScope(dispatcher)
}
