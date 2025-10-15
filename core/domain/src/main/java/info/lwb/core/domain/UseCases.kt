/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.core.domain

import info.lwb.core.common.Result
import info.lwb.core.model.Annotation
import info.lwb.core.model.Article
import info.lwb.core.model.MenuItem
import info.lwb.core.model.ThreadMessage
import kotlinx.coroutines.flow.Flow

/**
 * Use case that exposes a reactive stream of all locally cached [Article] instances.
 *
 * Delegates directly to [ArticleRepository.getArticles]. The returned [Flow]:
 *  - Emits a loading [Result] first where applicable (implementation dependent).
 *  - Emits subsequent success values whenever the underlying storage changes.
 *  - Emits failure results if the repository surfaces retrieval errors (e.g. database I/O).
 */
class GetArticlesUseCase(private val articleRepository: ArticleRepository) {
    /** Invoke the use case.
     * @return a cold [Flow] emitting [Result] wrapping lists of [Article].
     */
    operator fun invoke(): Flow<Result<List<Article>>> = articleRepository.getArticles()
}

/**
 * Triggers a foreground refresh of article metadata/content from the remote source.
 *
 * Acts as a fire‑and‑forget command – the caller can surface progress separately via
 * repository level reactive streams.
 */
class RefreshArticlesUseCase(private val articleRepository: ArticleRepository) {
    /** Execute a refresh. Propagates any exception thrown by the repository. */
    suspend operator fun invoke() {
        articleRepository.refreshArticles()
    }
}

/**
 * Performs a local search across indexed article metadata. Remote network calls are not
 * performed – this is expected to be fast and suitable for incremental search suggestions.
 */
class SearchArticlesUseCase(private val articleRepository: ArticleRepository) {
    /**
     * @param query free‑text query string.
     * @param limit maximum number of hits to return (pagination size).
     * @param offset starting offset for pagination.
     */
    suspend operator fun invoke(query: String, limit: Int = 25, offset: Int = 0) =
        articleRepository.searchLocal(query, limit, offset)
}

/**
 * Lists articles associated with an arbitrary label/tag. Primarily used for taxonomy views.
 */
class GetArticlesByLabelUseCase(private val articleRepository: ArticleRepository) {
    /**
     * Returns a snapshot list filtered by label (case-insensitive contains semantics).
     * Local snapshot only (offline friendly). Remote refresh is triggered elsewhere explicitly.
     */
    suspend operator fun invoke(label: String): List<Article> {
        val q = label.trim()
        if (q.isEmpty()) {
            return emptyList()
        }
        val snapshot = articleRepository.snapshotArticles()
        val filtered = snapshot.filter { a ->
            val lbl = a.label?.trim().orEmpty()
            lbl.equals(q, ignoreCase = true) || (lbl.isNotEmpty() && lbl.contains(q, ignoreCase = true))
        }
        return filtered.sortedWith(
            compareBy<Article> { it.order }
                .thenBy { it.title },
        )
    }
}

/**
 * Reactive stream of annotations for a given article enabling collaborative (private) markup.
 */
class GetAnnotationsUseCase(private val annotationRepository: AnnotationRepository) {
    /**
     * @param articleId id of target article.
     * @return a [Flow] of [Annotation] lists wrapped in [Result].
     */
    operator fun invoke(articleId: String): Flow<Result<List<Annotation>>> =
        annotationRepository.getAnnotations(articleId)
}

/**
 * Creates an annotation referencing a text range within an article.
 */
class AddAnnotationUseCase(private val annotationRepository: AnnotationRepository) {
    /**
     * @param articleId owning article.
     * @param startOffset inclusive character offset.
     * @param endOffset exclusive character offset.
     * @param anchorHash stable hash representing the anchor for conflict resolution.
     */
    suspend operator fun invoke(articleId: String, startOffset: Int, endOffset: Int, anchorHash: String) =
        annotationRepository.addAnnotation(articleId, startOffset, endOffset, anchorHash)
}

/**
 * Stream of messages associated with an annotation's discussion thread.
 */
class GetThreadMessagesUseCase(private val annotationRepository: AnnotationRepository) {
    /** @param annotationId id of the parent annotation. */
    operator fun invoke(annotationId: String): Flow<Result<List<ThreadMessage>>> =
        annotationRepository.getThreadMessages(annotationId)
}

/**
 * Adds a message (text or reference) to an annotation thread.
 */
class AddThreadMessageUseCase(private val annotationRepository: AnnotationRepository) {
    /**
     * @param annotationId thread parent.
     * @param type content type identifier (e.g. "text", "image").
     * @param contentRef reference to stored content or inline payload id.
     */
    suspend operator fun invoke(annotationId: String, type: String, contentRef: String) =
        annotationRepository.addThreadMessage(annotationId, type, contentRef)
}

/**
 * Provides a reactive stream of navigational menu items.
 */
class GetMenuUseCase(private val menuRepository: MenuRepository) {
    /** @return a [Flow] of menu item list [Result] updates. */
    operator fun invoke(): Flow<Result<List<MenuItem>>> = menuRepository.getMenuItems()
}

/**
 * Explicit refresh trigger for navigation menu data (e.g. remote taxonomy update).
 */
class RefreshMenuUseCase(private val menuRepository: MenuRepository) {
    /** Execute the refresh. */
    suspend operator fun invoke() = menuRepository.refreshMenu()
}
