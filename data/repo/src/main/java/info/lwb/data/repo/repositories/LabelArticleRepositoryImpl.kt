/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.data.repo.repositories

import info.lwb.core.domain.LabelArticleRepository
import info.lwb.core.model.Article
import info.lwb.data.network.ArticleApi
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple implementation that reuses the manifest endpoint and filters by label.
 * It maps to local ArticleEntity for shape consistency with core.model.Article.
 */
class LabelArticleRepositoryImpl(
    private val api: ArticleApi,
    private val dao: ArticleDao,
) : LabelArticleRepository {
    override suspend fun listByLabel(label: String): List<Article> = withContext(Dispatchers.IO) {
    val manifest = runCatching { api.getManifest() }.map { it.items }.getOrElse { emptyList() }
        val q = label.trim()
        val wanted = manifest.filter { dto ->
            val lbl = (dto.label ?: "").trim()
            lbl.equals(q, ignoreCase = true) || (lbl.isNotEmpty() && q.isNotEmpty() && lbl.contains(q, ignoreCase = true))
        }
        // Ensure minimal rows exist locally for navigation/search consistency
        if (wanted.isNotEmpty()) {
            for (m in wanted) {
                dao.upsertArticle(
                    ArticleEntity(
                        id = m.id,
                        title = m.title,
                        slug = m.slug,
                        version = m.version,
                        updatedAt = m.updatedAt,
                        wordCount = m.wordCount,
                    ),
                )
            }
        }
    wanted.map { m -> Article(m.id, m.title, m.slug, m.version, m.updatedAt, m.wordCount, m.coverUrl, m.iconUrl) }
    }
}
