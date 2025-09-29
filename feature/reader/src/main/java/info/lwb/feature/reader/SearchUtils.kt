/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

@Deprecated("Use SearchHit.kt declarations directly", ReplaceWith("buildSearchHits(pages, allBlocks, query)"))
internal fun buildSearchHitsCompat(pages: List<Page>?, allBlocks: List<ContentBlock>, query: String): List<SearchHit> =
    buildSearchHits(pages, allBlocks, query)

// Keep file non-empty with multiple declarations to avoid ktlint filename rule complaining about class/file name.
@Deprecated("Use SearchHit data class from SearchHit.kt")
internal typealias LegacySearchHit = SearchHit
