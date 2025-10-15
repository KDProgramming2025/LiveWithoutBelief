/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemingTest {
    @Test
    fun `mergeHtmlAndCss injects into existing head`() {
        val html = """|<html><head><title>T</title></head><body><p>Hi</p></body></html>
        """.trimMargin()
        val css = "body{color:red;}"
        val merged = mergeHtmlAndCss(html, css)
        assertTrue(merged.contains("body{color:red;}"))
        // Ensure only one head closing tag remains
        val count = Regex("</head>", RegexOption.IGNORE_CASE).findAll(merged).count()
        assertEquals(1, count)
    }

    @Test
    fun `mergeHtmlAndCss adds head when missing`() {
        val html = "<body><p>Hi</p></body>"
        val css = "body{background:#fff;}"
        val merged = mergeHtmlAndCss(html, css)
        assertTrue(merged.contains("<head>"))
        assertTrue(merged.contains("body{background:#fff;}"))
    }
}
