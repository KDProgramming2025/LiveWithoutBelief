/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtUtilsTest {
    private fun buildJwt(exp: Long?): String {
        val headerJson = "{\"alg\":\"none\"}"
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.toByteArray())
        val payloadObj = if (exp == null) {
            "{}"
        } else {
            "{\"exp\":$exp}"
        }
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadObj.toByteArray())
        return "$header.$payload" // omit trailing dot to simplify splitting
    }

    @Test fun extractExpirySuccess() {
        val now = System.currentTimeMillis() / 1000L
        val later = now + 3600
        val jwt = buildJwt(later)
        val exp = JwtUtils.extractExpiryEpochSeconds(jwt)
        println("JWT exp parsed=$exp token=$jwt")
        assertNotNull("exp was null for token=$jwt", exp)
        assertTrue("exp not greater than now (exp=$exp now=$now)", exp!! > now)
    }

    @Test fun extractExpiryMissing() {
        val jwt = buildJwt(null)
        assertNull(JwtUtils.extractExpiryEpochSeconds(jwt))
    }
}
