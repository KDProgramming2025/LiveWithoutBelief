/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import org.junit.Assert.assertTrue
import org.junit.Test

class AltchaSolverTest {
    @Test
    fun `solves simple sha256 prefix`() {
        // Chosen so the solution exists within a small range
        val salt = "salt-"
        // Find an n such that sha256(salt+n) starts with "00" within 100000
        val n = solveAltcha("SHA-256", "00", salt, 100_000)
        // Verify
        val check = java.security.MessageDigest.getInstance("SHA-256")
            .digest((salt + n.toString()).toByteArray())
            .joinToString("") { b -> "%02x".format(b) }
        assertTrue(check.startsWith("00"))
    }

    @Test
    fun `solves simple sha1 prefix`() {
        val salt = "alpha-"
        val n = solveAltcha("SHA-1", "0", salt, 50_000)
        val check = java.security.MessageDigest.getInstance("SHA-1")
            .digest((salt + n.toString()).toByteArray())
            .joinToString("") { b -> "%02x".format(b) }
        assertTrue(check.startsWith("0"))
    }
}
