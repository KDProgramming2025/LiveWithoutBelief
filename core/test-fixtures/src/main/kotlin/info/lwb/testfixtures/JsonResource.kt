/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.testfixtures

import java.nio.charset.StandardCharsets

/** Helpers for loading small JSON resources from the test classpath. */
object JsonResource {
    /**
     * Reads the resource at [path] from the classpath (must start with '/').
     *
     * @throws IllegalArgumentException if the resource cannot be located.
     */
    fun fromClasspath(path: String): String {
        val stream = requireNotNull(javaClass.getResourceAsStream(path)) {
            "Resource not found: $path"
        }
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}
