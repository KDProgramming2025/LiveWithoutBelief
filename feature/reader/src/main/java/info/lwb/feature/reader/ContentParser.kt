/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class Heading(val level: Int, val text: String) : ContentBlock
    data class Image(val url: String, val alt: String?) : ContentBlock
    data class Audio(val url: String) : ContentBlock
    data class YouTube(val videoId: String) : ContentBlock
}

private val headingRegex = Regex("<h([1-6])[^>]*>(.*?)</h\\1>", RegexOption.IGNORE_CASE)
private val imageRegex = Regex(
    "<img[^>]*src=\"(.*?)\"[^>]*?(?:alt=\"(.*?)\")?[^>]*>",
    RegexOption.IGNORE_CASE,
)
private val audioRegex = Regex(
    "<audio[^>]*src=\"(.*?)\"[^>]*>(?:.*?)</audio>",
    RegexOption.IGNORE_CASE,
)
private val youtubeRegex = Regex(
    "<iframe[^>]*src=\"https?://www.youtube.com/embed/" +
        "([a-zA-Z0-9_-]{6,})\"[^>]*></iframe>",
    RegexOption.IGNORE_CASE,
)

fun parseHtmlToBlocks(html: String): List<ContentBlock> {
    if (html.isBlank()) return emptyList()
    val blocks = mutableListOf<ContentBlock>()
    // Sequential scan: find earliest next tag among supported types; consume text between tags as paragraphs.
    val tagRegex = Regex(
        "(<h[1-6][^>]*>.*?</h[1-6]>|<img[^>]*?>|<audio[^>]*?>.*?</audio>|" +
            "<iframe[^>]*youtube.com/embed/.*?</iframe>|<p[^>]*>.*?</p>)",
        RegexOption.IGNORE_CASE,
    )
    var lastIndex = 0
    tagRegex.findAll(html).forEach { m ->
        if (m.range.first > lastIndex) {
            val between = stripTags(html.substring(lastIndex, m.range.first)).trim()
            if (between.isNotBlank()) blocks += ContentBlock.Paragraph(between)
        }
        val tag = m.value
        when {
            tag.startsWith("<h", ignoreCase = true) -> {
                val hm = headingRegex.find(tag)
                if (hm != null) {
                    blocks += ContentBlock.Heading(
                        hm.groupValues[1].toInt(),
                        stripTags(hm.groupValues[2]).trim(),
                    )
                }
            }
            tag.startsWith("<img", ignoreCase = true) -> {
                val im = imageRegex.find(tag)
                if (im != null) blocks += ContentBlock.Image(im.groupValues[1], im.groupValues.getOrNull(2))
            }
            tag.startsWith("<audio", ignoreCase = true) -> {
                val am = audioRegex.find(tag)
                if (am != null) blocks += ContentBlock.Audio(am.groupValues[1])
            }
            tag.startsWith("<iframe", ignoreCase = true) -> {
                val ym = youtubeRegex.find(tag)
                if (ym != null) blocks += ContentBlock.YouTube(ym.groupValues[1])
            }
            tag.startsWith("<p", ignoreCase = true) -> {
                val pm = Regex("<p[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE).find(tag)
                if (pm != null) {
                    val text = stripTags(pm.groupValues[1]).trim()
                    // Drop paragraphs that are only whitespace after trimming
                    if (text.isNotEmpty()) blocks += ContentBlock.Paragraph(text)
                }
            }
        }
        lastIndex = m.range.last + 1
    }
    if (lastIndex < html.length) {
        val trailing = stripTags(html.substring(lastIndex)).trim()
        if (trailing.isNotBlank()) blocks += ContentBlock.Paragraph(trailing)
    }
    // Final clean-up: drop any paragraphs that somehow remained blank/whitespace after trimming
    return blocks.filterNot { it is ContentBlock.Paragraph && it.text.isBlank() }
}

private fun stripTags(s: String): String = s.replace(Regex("<[^>]+>"), "")

// Pagination: naive char-based pagination respecting font scale.
data class Page(val index: Int, val blocks: List<ContentBlock>)

fun paginate(blocks: List<ContentBlock>, fontScale: Double, targetCharsPerPage: Int = 1800): List<Page> {
    if (blocks.isEmpty()) return emptyList()
    val adjustedTarget = (targetCharsPerPage / fontScale).toInt().coerceAtLeast(400)
    val pages = mutableListOf<Page>()
    var accChars = 0
    val current = mutableListOf<ContentBlock>()
    blocks.forEach { b ->
        val len = when (b) {
            is ContentBlock.Paragraph -> b.text.length
            is ContentBlock.Heading -> b.text.length
            is ContentBlock.Image -> 80 // heuristic footprint
            is ContentBlock.Audio -> 60
            is ContentBlock.YouTube -> 120
        }
        if (accChars + len > adjustedTarget && current.isNotEmpty()) {
            pages.add(Page(pages.size, current.toList()))
            current.clear()
            accChars = 0
        }
        current += b
        accChars += len
    }
    if (current.isNotEmpty()) pages.add(Page(pages.size, current.toList()))
    return pages
}

// Heading extraction from pages for Table of Contents / navigation
data class HeadingItem(val level: Int, val text: String, val pageIndex: Int)

fun buildHeadingItems(pages: List<Page>): List<HeadingItem> = pages.flatMap { page ->
    page.blocks.filterIsInstance<ContentBlock.Heading>().map { h -> HeadingItem(h.level, h.text, page.index) }
}
