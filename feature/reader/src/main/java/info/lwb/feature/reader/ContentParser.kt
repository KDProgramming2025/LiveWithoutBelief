/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

// Reader content model sealed hierarchy (internal – not exposed as library API)
internal sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock

    data class Heading(val level: Int, val text: String) : ContentBlock

    data class Image(val url: String, val alt: String?) : ContentBlock

    data class Audio(val url: String) : ContentBlock

    data class YouTube(val videoId: String) : ContentBlock
}

private val headingRegex = Regex(
    "<h([1-6])[^>]*>(.*?)</h\\1>",
    RegexOption.IGNORE_CASE,
)

private val imageRegex = Regex(
    "<img[^>]*src=\"(.*?)\"[^>]*?(?:alt=\"(.*?)\")?[^>]*>",
    RegexOption.IGNORE_CASE,
)

private val audioRegex = Regex(
    "<audio[^>]*src=\"(.*?)\"[^>]*>(?:.*?)</audio>",
    RegexOption.IGNORE_CASE,
)

private val youtubeRegex = Regex(
    "<iframe[^>]*src=\"https?://www.youtube.com/embed/([a-zA-Z0-9_-]{6,})\"[^>]*></iframe>",
    RegexOption.IGNORE_CASE,
)

// Combined tag regex for a single scan pass.
private val compositeTagRegex = Regex(
    "(" +
        "<h[1-6][^>]*>.*?</h[1-6]>" +
        "|<img[^>]*?>" +
        "|<audio[^>]*?>.*?</audio>" +
        "|<iframe[^>]*youtube.com/embed/.*?</iframe>" +
        "|<p[^>]*>.*?</p>" +
        ")",
    RegexOption.IGNORE_CASE,
)

private val paragraphRegex = Regex("<p[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE)

/**
 * Parse simplified HTML produced by the server into a linear list of [ContentBlock]s.
 * This is a pragmatic, regex-driven parser assuming trusted, well‑formed HTML subset.
 */
internal fun parseHtmlToBlocks(html: String): List<ContentBlock> {
    if (html.isBlank()) {
        return emptyList()
    }
    val out = mutableListOf<ContentBlock>()
    extractBlocks(html, out)
    // Filter blank paragraphs defensively.
    return out.filterNot { it is ContentBlock.Paragraph && it.text.isBlank() }
}

private fun extractBlocks(html: String, out: MutableList<ContentBlock>) {
    var lastIndex = 0
    compositeTagRegex.findAll(html).forEach { match ->
        appendInterTagParagraph(html, lastIndex, match.range.first, out)
        processTag(match.value, out)
        lastIndex = match.range.last + 1
    }
    appendTrailingParagraph(html, lastIndex, out)
}

private fun appendInterTagParagraph(html: String, start: Int, end: Int, out: MutableList<ContentBlock>) {
    if (end > start) {
        val between = stripTags(html.substring(start, end)).trim()
        if (between.isNotBlank()) {
            out += ContentBlock.Paragraph(between)
        }
    }
}

private fun appendTrailingParagraph(html: String, lastIndex: Int, out: MutableList<ContentBlock>) {
    if (lastIndex < html.length) {
        val trailing = stripTags(html.substring(lastIndex)).trim()
        if (trailing.isNotBlank()) {
            out += ContentBlock.Paragraph(trailing)
        }
    }
}

private fun processTag(tag: String, out: MutableList<ContentBlock>) {
    when {
        tag.startsWith("<h", ignoreCase = true) -> {
            val hm = headingRegex.find(tag)
            if (hm != null) {
                out += ContentBlock.Heading(
                    hm.groupValues[1].toInt(),
                    stripTags(hm.groupValues[2]).trim(),
                )
            }
        }
        tag.startsWith("<img", ignoreCase = true) -> {
            val im = imageRegex.find(tag)
            if (im != null) {
                out += ContentBlock.Image(im.groupValues[1], im.groupValues.getOrNull(2))
            }
        }
        tag.startsWith("<audio", ignoreCase = true) -> {
            val am = audioRegex.find(tag)
            if (am != null) {
                out += ContentBlock.Audio(am.groupValues[1])
            }
        }
        tag.startsWith("<iframe", ignoreCase = true) -> {
            val ym = youtubeRegex.find(tag)
            if (ym != null) {
                out += ContentBlock.YouTube(ym.groupValues[1])
            }
        }
        tag.startsWith("<p", ignoreCase = true) -> {
            val pm = paragraphRegex.find(tag)
            if (pm != null) {
                val text = stripTags(pm.groupValues[1]).trim()
                if (text.isNotEmpty()) {
                    out += ContentBlock.Paragraph(text)
                }
            }
        }
    }
}

private fun stripTags(s: String): String = s.replace(Regex("<[^>]+>"), "")

// Pagination: naive char-based pagination respecting font scale.
internal data class Page(val index: Int, val blocks: List<ContentBlock>)

private const val DEFAULT_TARGET_CHARS_PER_PAGE = 1800
private const val MIN_CHARS_PER_PAGE = 400
private const val IMAGE_CHAR_FOOTPRINT = 80
private const val AUDIO_CHAR_FOOTPRINT = 60
private const val YOUTUBE_CHAR_FOOTPRINT = 120

/**
 * Naive pagination splitting blocks into pages based on an approximate character budget that
 * adjusts for the current font scale. Media blocks use heuristic character footprints.
 */
internal fun paginate(
    blocks: List<ContentBlock>,
    fontScale: Double,
    targetCharsPerPage: Int = DEFAULT_TARGET_CHARS_PER_PAGE,
): List<Page> {
    if (blocks.isEmpty()) {
        return emptyList()
    }
    val adjustedTarget = (targetCharsPerPage / fontScale).toInt().coerceAtLeast(MIN_CHARS_PER_PAGE)
    val pages = mutableListOf<Page>()
    var accChars = 0
    val current = mutableListOf<ContentBlock>()
    blocks.forEach { b ->
        val len = when (b) {
            is ContentBlock.Paragraph -> {
                b.text.length
            }
            is ContentBlock.Heading -> {
                b.text.length
            }
            is ContentBlock.Image -> {
                IMAGE_CHAR_FOOTPRINT // heuristic footprint
            }
            is ContentBlock.Audio -> {
                AUDIO_CHAR_FOOTPRINT
            }
            is ContentBlock.YouTube -> {
                YOUTUBE_CHAR_FOOTPRINT
            }
        }
        if (accChars + len > adjustedTarget && current.isNotEmpty()) {
            pages.add(Page(pages.size, current.toList()))
            current.clear()
            accChars = 0
        }
        current += b
        accChars += len
    }
    if (current.isNotEmpty()) {
        pages.add(Page(pages.size, current.toList()))
    }
    return pages
}

// Heading extraction from pages for Table of Contents / navigation
internal data class HeadingItem(val level: Int, val text: String, val pageIndex: Int)

internal fun buildHeadingItems(pages: List<Page>): List<HeadingItem> = pages.flatMap { page ->
    page.blocks
        .filterIsInstance<ContentBlock.Heading>()
        .map { h -> HeadingItem(h.level, h.text, page.index) }
}
