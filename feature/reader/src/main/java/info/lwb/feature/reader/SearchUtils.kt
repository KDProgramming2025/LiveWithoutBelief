package info.lwb.feature.reader

/** Represents a single search occurrence anchored to a page and block (paragraph) index. */
data class SearchHit(val pageIndex: Int, val blockIndex: Int, val range: IntRange)

/** Build ordered search hits across pages (or single list of blocks if pages null). */
fun buildSearchHits(pages: List<Page>?, allBlocks: List<ContentBlock>, query: String): List<SearchHit> {
    if (query.isBlank()) return emptyList()
    val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
    val hits = mutableListOf<SearchHit>()
    if (pages != null && pages.isNotEmpty()) {
        pages.forEach { page ->
            page.blocks.forEachIndexed { blockIdx, block ->
                if (block is ContentBlock.Paragraph) {
                    regex.findAll(block.text).forEach { m ->
                        hits += SearchHit(page.index, blockIdx, m.range)
                    }
                }
            }
        }
    } else {
        // Treat entire article as single page 0
        allBlocks.forEachIndexed { blockIdx, block ->
            if (block is ContentBlock.Paragraph) {
                regex.findAll(block.text).forEach { m ->
                    hits += SearchHit(0, blockIdx, m.range)
                }
            }
        }
    }
    return hits
}
