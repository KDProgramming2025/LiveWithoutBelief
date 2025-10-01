/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import java.security.MessageDigest

/**
 * Solve an ALTCHA-style puzzle by brute-forcing a number such that
 * hex(hash(salt + number)) starts with the provided challenge prefix.
 *
 * algorithm: One of SHA-1, SHA-256, SHA-512 (case-insensitive; defaults to SHA-256)
 * challengePrefix: Lowercase hexadecimal prefix to match
 * salt: Arbitrary string provided by server in challenge
 * max: Maximum number (inclusive) to try
 *
 * Returns the first number found in [0, max] that satisfies the condition,
 * or throws if not found within the range.
 */
internal fun solveAltcha(algorithm: String, challengePrefix: String, salt: String, max: Long): Long {
    val algo = when (algorithm.uppercase()) {
        "SHA-1" -> {
            "SHA-1"
        }
        "SHA-512" -> {
            "SHA-512"
        }
        else -> {
            "SHA-256"
        }
    }
    val md = MessageDigest.getInstance(algo)
    for (n in 0L..max) {
        md.reset()
        md.update((salt + n.toString()).toByteArray(Charsets.UTF_8))
        val h = md.digest().toHexLower()
        if (h.startsWith(challengePrefix)) {
            return n
        }
    }
    error("ALTCHA solution not found up to max=$max")
}

private const val HEX_TABLE: String = "0123456789abcdef"
private const val BYTE_MASK: Int = 0xFF
private const val HALF_BYTE_SHIFT: Int = 4
private const val LOWER_NIBBLE_MASK: Int = 0x0F

private fun ByteArray.toHexLower(): String = joinToString(separator = "") { b ->
    val i = b.toInt() and BYTE_MASK
    val hi = HEX_TABLE[i ushr HALF_BYTE_SHIFT]
    val lo = HEX_TABLE[i and LOWER_NIBBLE_MASK]
    "$hi$lo"
}
