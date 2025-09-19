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
    fun extractExpiryEpochSeconds(idToken: String): Long? {
        return try {
            val rawParts = idToken.split('.')
            // Accept at least 2 segments (header.payload). Signature may be omitted.
            if (rawParts.size < 2) return null
            val parts = rawParts
            val payloadB64 = parts[1]
            val rem = payloadB64.length % 4
            val padded = if (rem == 0) payloadB64 else payloadB64 + "=".repeat(4 - rem)
            val decoded = JvmB64.decoder.decode(padded)
            val jsonStr = String(decoded, Charsets.UTF_8)
            // Very small manual parse for "exp":<number>
            val keyIdx = jsonStr.indexOf("\"exp\"")
            if (keyIdx == -1) return null
            val colonIdx = jsonStr.indexOf(':', keyIdx)
            if (colonIdx == -1) return null
            var i = colonIdx + 1
            while (i < jsonStr.length && jsonStr[i].isWhitespace()) i++
            if (i >= jsonStr.length) return null
            val start = i
            while (i < jsonStr.length && jsonStr[i].isDigit()) i++
            if (start == i) return null
            jsonStr.substring(start, i).toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun extractSubject(token: String): String? {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return null
            val payloadB64 = parts[1]
            val rem = payloadB64.length % 4
            val padded = if (rem == 0) payloadB64 else payloadB64 + "=".repeat(4 - rem)
            val decoded = JvmB64.decoder.decode(padded)
            val jsonStr = String(decoded, Charsets.UTF_8)
            val key = "\"sub\""
            val idx = jsonStr.indexOf(key)
            if (idx == -1) return null
            val colon = jsonStr.indexOf(':', idx)
            if (colon == -1) return null
            var i = colon + 1
            while (i < jsonStr.length && jsonStr[i].isWhitespace()) i++
            if (i >= jsonStr.length) return null
            // subject may be quoted string
            return if (jsonStr[i] == '"') {
                i++
                val start = i
                while (i < jsonStr.length && jsonStr[i] != '"') i++
                if (i >= jsonStr.length) null else jsonStr.substring(start, i)
            } else {
                val start = i
                while (i < jsonStr.length && !jsonStr[i].isWhitespace() && jsonStr[i] != ',' && jsonStr[i] != '}') i++
                jsonStr.substring(start, i)
            }
        } catch (_: Exception) {
            null
        }
    }
}
