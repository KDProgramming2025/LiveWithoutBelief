/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.testfixtures

import java.nio.charset.StandardCharsets

object JsonResource {
    fun fromClasspath(path: String): String {
        val stream = requireNotNull(javaClass.getResourceAsStream(path)) {
            "Resource not found: $path"
        }
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}
