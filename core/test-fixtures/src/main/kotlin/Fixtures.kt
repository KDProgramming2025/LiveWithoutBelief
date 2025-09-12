/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.testfixtures

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

object Fixtures {
    val dispatcher = StandardTestDispatcher()
    fun testScope() = TestScope(dispatcher)
}
