/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access layer for article metadata, auxiliary media assets and localized search.
 */
@Suppress("ComplexInterface")
@Dao
interface ArticleDao {
    /** Fetch a single article row by its stable identifier. */
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticle(id: String): ArticleEntity?

    /** List all article rows currently cached locally. */
    @Query("SELECT * FROM articles")
    suspend fun listArticles(): List<ArticleEntity>

    /** Observe all article rows; emits on every table change. */
    @Query("SELECT * FROM articles")
    fun observeArticles(): Flow<List<ArticleEntity>>

    /** Insert or replace a single article metadata record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(article: ArticleEntity)

    // Asset management removed

    /** Delete article rows whose id is not in the keep set (authoritative pruning). */
    @Query("DELETE FROM articles WHERE id NOT IN (:keepIds)")
    suspend fun deleteArticlesNotIn(keepIds: List<String>)

    /** Remove all article rows (full reset). */
    @Query("DELETE FROM articles")
    suspend fun clearAllArticles()

    /** Title-only LIKE-based local search (body removed). */
    @Query(
        "SELECT a.id AS id, a.title AS title, a.slug AS slug, a.version AS version, " +
            "a.updatedAt AS updatedAt, a.wordCount AS wordCount, " +
            "a.label AS label, a.`order` AS ordering, a.coverUrl AS coverUrl, " +
            "a.iconUrl AS iconUrl, a.indexUrl AS indexUrl " +
            "FROM articles a " +
            "WHERE a.title LIKE '%' || :q || '%' " +
            "ORDER BY a.updatedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchArticlesLike(q: String, limit: Int, offset: Int): List<ArticleSearchRow>
}

/**
 * Projection row returned by [ArticleDao.searchArticlesLike].
 * All properties map 1:1 with the selected columns in the query.
 */
data class ArticleSearchRow(
    /** Article primary identifier. */
    val id: String,
    /** Current localized display title. */
    val title: String,
    /** URL friendly slug used in links. */
    val slug: String,
    /** Monotonic content/version integer. */
    val version: Int,
    /** ISO-8601 last update timestamp. */
    val updatedAt: String,
    /** Estimated word count for reading time heuristics. */
    val wordCount: Int,
    /** Optional category/label badge. */
    val label: String?,
    /** Explicit ordering index (ascending). */
    val ordering: Int?,
    /** Cover image URL (nullable for legacy rows). */
    val coverUrl: String?,
    /** Icon/thumbnail image URL. */
    val iconUrl: String?,
    /** Pre-rendered index HTML URL. */
    val indexUrl: String?,
)

/** Data access for per-user inline text/image annotations linked to articles. */
@Dao
interface AnnotationDao {
    /** Observe all annotations authored by a user for a specific article id. */
    @Query("SELECT * FROM annotations WHERE userId = :userId AND articleId = :articleId")
    fun observeAnnotations(userId: String, articleId: String): Flow<List<AnnotationEntity>>

    /** Upsert (add or replace) an annotation row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(annotation: AnnotationEntity)
}
