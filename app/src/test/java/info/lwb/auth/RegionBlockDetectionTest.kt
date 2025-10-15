/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionBlockDetectionTest {
    @Test
    fun detects403Forbidden() {
        val ex = RuntimeException("403 Forbidden verifyAssertion permission denied")
        assertTrue(testIsRegionBlocked(ex))
    }

    @Test
    fun detectsNestedCause() {
        val inner = IllegalStateException(
            "Your client does not have permission to get URL /identitytoolkit/v3/" +
                "relyingparty/verifyAssertion from this server. 403",
        )
        val outer = RuntimeException("Wrapper", inner)
        assertTrue(testIsRegionBlocked(outer))
    }

    @Test
    fun nonBlockingErrorFalse() {
        val ex = RuntimeException("network timeout")
        assertFalse(testIsRegionBlocked(ex))
    }
}
