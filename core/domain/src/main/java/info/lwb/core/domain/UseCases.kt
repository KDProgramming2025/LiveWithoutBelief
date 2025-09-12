/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.core.domain

import info.lwb.core.common.Result
import info.lwb.core.model.Annotation
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import info.lwb.core.model.Bookmark
import info.lwb.core.model.BookmarkFolder
import info.lwb.core.model.ThreadMessage
import kotlinx.coroutines.flow.Flow

class GetArticlesUseCase(private val articleRepository: ArticleRepository) {
    operator fun invoke(): Flow<Result<List<Article>>> = articleRepository.getArticles()
}

class GetArticleContentUseCase(private val articleRepository: ArticleRepository) {
    operator fun invoke(articleId: String): Flow<Result<ArticleContent>> =
        articleRepository.getArticleContent(articleId)
}

class RefreshArticlesUseCase(private val articleRepository: ArticleRepository) {
    suspend operator fun invoke() = articleRepository.refreshArticles()
}

class SearchArticlesUseCase(private val articleRepository: ArticleRepository) {
    suspend operator fun invoke(query: String, limit: Int = 25, offset: Int = 0) =
        articleRepository.searchLocal(query, limit, offset)
}

class GetBookmarksUseCase(private val bookmarkRepository: BookmarkRepository) {
    operator fun invoke(): Flow<Result<List<Bookmark>>> = bookmarkRepository.getBookmarks()
}

class AddBookmarkUseCase(private val bookmarkRepository: BookmarkRepository) {
    suspend operator fun invoke(articleId: String, folderId: String? = null) =
        bookmarkRepository.addBookmark(articleId, folderId)
}

class RemoveBookmarkUseCase(private val bookmarkRepository: BookmarkRepository) {
    suspend operator fun invoke(bookmarkId: String) = bookmarkRepository.removeBookmark(bookmarkId)
}

class GetBookmarkFoldersUseCase(private val bookmarkRepository: BookmarkRepository) {
    operator fun invoke(): Flow<Result<List<BookmarkFolder>>> = bookmarkRepository.getBookmarkFolders()
}

class CreateFolderUseCase(private val bookmarkRepository: BookmarkRepository) {
    suspend operator fun invoke(name: String) = bookmarkRepository.createFolder(name)
}

class MoveBookmarkUseCase(private val bookmarkRepository: BookmarkRepository) {
    suspend operator fun invoke(bookmarkId: String, folderId: String?) =
        bookmarkRepository.moveBookmark(bookmarkId, folderId)
}

class SearchBookmarkedUseCase(private val bookmarkRepository: BookmarkRepository) {
    suspend operator fun invoke(query: String, limit: Int = 25, offset: Int = 0) =
        bookmarkRepository.searchBookmarked(query, limit, offset)
}

class GetAnnotationsUseCase(private val annotationRepository: AnnotationRepository) {
    operator fun invoke(articleId: String): Flow<Result<List<Annotation>>> =
        annotationRepository.getAnnotations(articleId)
}

class AddAnnotationUseCase(private val annotationRepository: AnnotationRepository) {
    suspend operator fun invoke(articleId: String, startOffset: Int, endOffset: Int, anchorHash: String) =
        annotationRepository.addAnnotation(articleId, startOffset, endOffset, anchorHash)
}

class GetThreadMessagesUseCase(private val annotationRepository: AnnotationRepository) {
    operator fun invoke(annotationId: String): Flow<Result<List<ThreadMessage>>> =
        annotationRepository.getThreadMessages(annotationId)
}

class AddThreadMessageUseCase(private val annotationRepository: AnnotationRepository) {
    suspend operator fun invoke(annotationId: String, type: String, contentRef: String) =
        annotationRepository.addThreadMessage(annotationId, type, contentRef)
}
