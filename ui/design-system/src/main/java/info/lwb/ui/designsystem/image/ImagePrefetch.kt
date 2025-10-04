/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.ui.designsystem.image

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import info.lwb.core.model.Article
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Prefetches cover/icon images for upcoming articles to warm caches. */
fun prefetchArticleImages(
    context: Context,
    imageLoader: ImageLoader,
    articles: List<Article>,
    limit: Int = 6,
): Job {
    val scope = CoroutineScope(Dispatchers.IO)
    val slice = if (articles.isEmpty()) {
        emptyList()
    } else {
        val limited = articles.take(limit)
        limited
    }
    return scope.launch {
        val deferred: MutableList<Deferred<Unit>> = ArrayList(slice.size)
        for (a in slice) {
            val job = async {
                val coverBuilder = ImageRequest.Builder(context)
                coverBuilder.data(a.coverUrl)
                val cover = coverBuilder.build()
                imageLoader.enqueue(cover)
                val iconBuilder = ImageRequest.Builder(context)
                iconBuilder.data(a.iconUrl)
                val icon = iconBuilder.build()
                imageLoader.enqueue(icon)
            }
            deferred += job
        }
        deferred.awaitAll()
    }
}
