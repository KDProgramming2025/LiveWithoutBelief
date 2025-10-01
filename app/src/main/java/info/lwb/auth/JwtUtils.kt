/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

private object JvmB64 {
    val decoder = java.util.Base64.getUrlDecoder()
}

/** Lightweight JWT helper (no signature validation) to extract numeric exp claim (epoch seconds). */
object JwtUtils {
    /**
     * Extracts the numeric `exp` (expiry epoch seconds) claim from a JWT *without* validating
     * the signature. Returns null if:
     * - The token has fewer than two segments
     * - The base64 payload segment cannot be decoded
     * - The `exp` field is missing or not a valid positive integer
     */
    fun extractExpiryEpochSeconds(idToken: String): Long? = runCatching {
        val json = decodePayloadOrNull(idToken) ?: return null
        extractNumericClaim(json, EXP_KEY)
    }.getOrNull()

    /**
     * Extracts the `sub` (subject) claim from a JWT *without* validating the signature.
     * Handles either a quoted JSON string or an unquoted token up to a delimiter (comma / closing brace / whitespace).
     */
    fun extractSubject(token: String): String? = runCatching {
        val json = decodePayloadOrNull(token) ?: return null
        extractStringClaim(json, SUB_KEY)
    }.getOrNull()

    // region internal helpers ------------------------------------------------------------------------------------

    private const val SEGMENT_SEPARATOR = '.'
    private const val MIN_SEGMENTS = 2
    private const val PADDING_GROUP = 4
    private const val PAD_CHAR = '='
    private const val EXP_KEY = "\"exp\""
    private const val SUB_KEY = "\"sub\""

    private fun findColonAfterKey(json: String, key: String): Int? {
        val keyIdx = json.indexOf(key)
        if (keyIdx == -1) {
            return null
        }
        val colonIdx = json.indexOf(':', keyIdx)
        if (colonIdx == -1) {
            return null
        }
        return colonIdx
    }

    private fun skipWhitespace(json: String, start: Int): Int? {
        var i = start
        while (i < json.length && json[i].isWhitespace()) {
            i++
        }
        return if (i >= json.length) {
            null
        } else {
            i
        }
    }

    private fun decodePayloadOrNull(jwt: String): String? {
        var result: String? = null
        val parts = jwt.split(SEGMENT_SEPARATOR)
        if (parts.size >= MIN_SEGMENTS) {
            val payloadB64 = parts[1]
            val rem = payloadB64.length % PADDING_GROUP
            val padded = if (rem == 0) {
                payloadB64
            } else {
                payloadB64 + PAD_CHAR.toString().repeat(PADDING_GROUP - rem)
            }
            val decoded = runCatching { JvmB64.decoder.decode(padded) }.getOrNull()
            if (decoded != null) {
                result = String(decoded, Charsets.UTF_8)
            }
        }
        return result
    }

    private fun extractNumericClaim(json: String, key: String): Long? {
        val colonIdx = findColonAfterKey(json, key)
        val start = if (colonIdx != null) {
            skipWhitespace(json, colonIdx + 1)
        } else {
            null
        }
        if (colonIdx == null || start == null) {
            return null
        }
        var i = start
        while (i < json.length && json[i].isDigit()) {
            i++
        }
        if (i == start) {
            return null
        }
        return json.substring(start, i).toLongOrNull()
    }

    private fun extractStringClaim(json: String, key: String): String? {
        val colonIdx = findColonAfterKey(json, key)
        val start = if (colonIdx != null) {
            skipWhitespace(json, colonIdx + 1)
        } else {
            null
        }
        if (colonIdx == null || start == null) {
            return null
        }
        if (json[start] == '"') {
            return extractQuoted(json, start + 1)
        }
        return extractUnquoted(json, start)
    }

    private fun extractQuoted(json: String, startIndex: Int): String? {
        var i = startIndex
        while (i < json.length && json[i] != '"') {
            i++
        }
        return if (i >= json.length) {
            null
        } else {
            json.substring(startIndex, i)
        }
    }

    private fun extractUnquoted(json: String, startIndex: Int): String {
        var i = startIndex
        while (i < json.length && !isUnquotedTerminator(json[i])) {
            i++
        }
        return json.substring(startIndex, i)
    }

    private fun isUnquotedTerminator(c: Char): Boolean = c.isWhitespace() || c == ',' || c == '}'

    // endregion --------------------------------------------------------------------------------------------------
}
