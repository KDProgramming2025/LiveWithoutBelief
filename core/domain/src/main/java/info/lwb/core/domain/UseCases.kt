package info.lwb.core.domain

import info.lwb.core.common.Result
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import info.lwb.core.model.Bookmark
import info.lwb.core.model.BookmarkFolder
import info.lwb.core.model.Annotation
import info.lwb.core.model.ThreadMessage
import kotlinx.coroutines.flow.Flow

class GetArticlesUseCase(private val articleRepository: ArticleRepository) {
    operator fun invoke(): Flow<Result<List<Article>>> = articleRepository.getArticles()
}

class GetArticleContentUseCase(private val articleRepository: ArticleRepository) {
    operator fun invoke(articleId: String): Flow<Result<ArticleContent>> = articleRepository.getArticleContent(articleId)
}

class RefreshArticlesUseCase(private val articleRepository: ArticleRepository) {
    suspend operator fun invoke() = articleRepository.refreshArticles()
}

class GetBookmarksUseCase(private val bookmarkRepository: BookmarkRepository) {
    operator fun invoke(): Flow<Result<List<Bookmark>>> = bookmarkRepository.getBookmarks()
}

class AddBookmarkUseCase(private val bookmarkRepository: BookmarkRepository) {
    suspend operator fun invoke(articleId: String, folderId: String? = null) = bookmarkRepository.addBookmark(articleId, folderId)
}

class GetAnnotationsUseCase(private val annotationRepository: AnnotationRepository) {
    operator fun invoke(articleId: String): Flow<Result<List<Annotation>>> = annotationRepository.getAnnotations(articleId)
}

class AddAnnotationUseCase(private val annotationRepository: AnnotationRepository) {
    suspend operator fun invoke(articleId: String, startOffset: Int, endOffset: Int, anchorHash: String) = annotationRepository.addAnnotation(articleId, startOffset, endOffset, anchorHash)
}
