package info.lwb.feature.reader

sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class Heading(val level: Int, val text: String) : ContentBlock
    data class Image(val url: String, val alt: String?) : ContentBlock
    data class Audio(val url: String) : ContentBlock
    data class YouTube(val videoId: String) : ContentBlock
}

private val headingRegex = Regex("<h([1-6])[^>]*>(.*?)</h\\1>", RegexOption.IGNORE_CASE)
private val imageRegex = Regex("<img[^>]*src=\"(.*?)\"[^>]*?(?:alt=\"(.*?)\")?[^>]*>", RegexOption.IGNORE_CASE)
private val audioRegex = Regex("<audio[^>]*src=\"(.*?)\"[^>]*>(?:.*?)</audio>", RegexOption.IGNORE_CASE)
private val youtubeRegex = Regex("<iframe[^>]*src=\"https?://www.youtube.com/embed/([a-zA-Z0-9_-]{6,})\"[^>]*></iframe>", RegexOption.IGNORE_CASE)

fun parseHtmlToBlocks(html: String): List<ContentBlock> {
    if (html.isBlank()) return emptyList()
    val blocks = mutableListOf<ContentBlock>()

    // Extract known blocks; fallback treat remaining text as paragraphs by splitting on double newlines
    var remaining = html
    // Headings
    headingRegex.findAll(html).forEach { m ->
        blocks.add(ContentBlock.Heading(m.groupValues[1].toInt(), stripTags(m.groupValues[2]).trim()))
    }
    imageRegex.findAll(html).forEach { m -> blocks.add(ContentBlock.Image(m.groupValues[1], m.groupValues.getOrNull(2))) }
    audioRegex.findAll(html).forEach { m -> blocks.add(ContentBlock.Audio(m.groupValues[1])) }
    youtubeRegex.findAll(html).forEach { m -> blocks.add(ContentBlock.YouTube(m.groupValues[1])) }

    // Simple paragraphs: remove tags we already processed and split
    remaining = html
        .replace(headingRegex, "")
        .replace(imageRegex, "")
        .replace(audioRegex, "")
        .replace(youtubeRegex, "")
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
    val paragraphRegex = Regex("<p[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE)
    val paragraphs = paragraphRegex.findAll(remaining).map { stripTags(it.groupValues[1]).trim() }.filter { it.isNotBlank() }.toList()
    if (paragraphs.isEmpty()) {
        val plain = stripTags(remaining)
        plain.split(/\n{2,}/.toRegex()).map { it.trim() }.filter { it.isNotBlank() }.forEach { blocks.add(ContentBlock.Paragraph(it)) }
    } else {
        paragraphs.forEach { blocks.add(ContentBlock.Paragraph(it)) }
    }
    return blocks
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