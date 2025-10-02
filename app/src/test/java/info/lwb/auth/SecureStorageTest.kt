/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureStorageTest {
    @Test
    fun roundTripTokenProfileAndExpiry() {
        val storage: SecureStorage = InMemorySecureStorage()
        assertNull(storage.getIdToken())
        assertNull(storage.getTokenExpiry())

        storage.putIdToken("tok123")
        storage.putTokenExpiry(12345L)
        storage.putProfile("Alice", "alice@example.com", "avatar.png")

        assertEquals("tok123", storage.getIdToken())
        assertEquals(12345L, storage.getTokenExpiry())
        val (name, email, avatar) = storage.getProfile()
        assertEquals("Alice", name)
        assertEquals("alice@example.com", email)
        assertEquals("avatar.png", avatar)

        storage.clear()
        assertNull(storage.getIdToken())
        assertNull(storage.getTokenExpiry())
        val cleared = storage.getProfile()
        assertNull(cleared.first)
        assertNull(cleared.second)
        assertNull(cleared.third)
    }
}
