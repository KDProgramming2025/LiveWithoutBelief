package info.lwb.feature.reader

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentParserTest {
    @Test
    fun parse_basicHtml_extractsBlocks() {
        val html = """
            <h1>Title</h1>
            <p>First paragraph.</p>
            <img src=\"https://example.com/img.png\" alt=\"Alt\" />
            <p>Second paragraph.</p>
            <audio src=\"https://example.com/a.mp3\"></audio>
            <iframe src=\"https://www.youtube.com/embed/abc12345\"></iframe>
        """.trimIndent()
        val blocks = parseHtmlToBlocks(html)
        // We don't enforce order strictly because we pool different regexes; ensure presence counts
        val headings = blocks.filterIsInstance<ContentBlock.Heading>()
        val paras = blocks.filterIsInstance<ContentBlock.Paragraph>()
        val imgs = blocks.filterIsInstance<ContentBlock.Image>()
        val aud = blocks.filterIsInstance<ContentBlock.Audio>()
        val yt = blocks.filterIsInstance<ContentBlock.YouTube>()
    // Assert at least expected counts (parser may include additional paragraphs if whitespace treated as paragraph)
    assertEquals(1, headings.size)
    assertEquals(2, paras.size) // exactly two <p> tags present
    assertEquals(1, imgs.size)
    assertEquals(1, aud.size)
    assertEquals(1, yt.size)
    }

    @Test
    fun paginate_respectsFontScale() {
        val blocks = (0 until 30).map { ContentBlock.Paragraph("Paragraph $it with some text to accumulate size.") }
        val pagesNormal = paginate(blocks, fontScale = 1.0)
        val pagesLarge = paginate(blocks, fontScale = 1.5)
        // Larger font -> fewer chars per page -> more pages
        assert(pagesLarge.size >= pagesNormal.size)
    }
}
