/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.ui.designsystem.image

import android.app.Application
import coil.ImageLoader
import coil.ImageLoader.Builder as ImageLoaderBuilder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides a centralized Coil [ImageLoader] for the app. */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    private const val MEMORY_CACHE_PERCENT = 0.25
    private const val DISK_CACHE_PERCENT = 0.02
    private const val DISK_CACHE_DIR = "image_cache"

    /** Builds the shared Coil [ImageLoader] with caching & SVG support. */
    @Provides
    @Singleton
    fun provideImageLoader(app: Application): ImageLoader {
        val builder = ImageLoaderBuilder(app)
        builder.crossfade(true)
        builder.respectCacheHeaders(true)
        builder.components { add(SvgDecoder.Factory()) }
        builder.memoryCache {
            val mc = MemoryCache.Builder(app)
            mc.maxSizePercent(MEMORY_CACHE_PERCENT)
            mc.build()
        }
        builder.diskCache {
            val dc = DiskCache.Builder()
            dc.directory(app.cacheDir.resolve(DISK_CACHE_DIR))
            dc.maxSizePercent(DISK_CACHE_PERCENT)
            dc.build()
        }
        return builder.build()
    }
}
