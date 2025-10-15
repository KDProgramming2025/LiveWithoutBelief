/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.network

import retrofit2.http.GET

/**
 * Retrofit API defining read-only endpoints for article content.
 *
 * Endpoints exposed here are used by higher layers to fetch:
 *  - The global article manifest (list of published articles & metadata)
 *  - Individual full article payloads (sections + media collections)
 *
 * All functions are suspend because they perform network I/O via Retrofit's coroutine adapter.
 */
interface ArticleApi {
    /**
     * Fetches the global manifest of articles.
     *
     * @return [ManifestResponse] describing every available published article (id, slug, version & meta).
     */
    @GET("v1/articles/manifest")
    suspend fun getManifest(): ManifestResponse
}

/**
 * Public menu API returning the navigational structure displayed in UI sidebars or menus.
 *
 * This surface is intentionally separate from [ArticleApi] to decouple navigation concerns from article
 * content delivery and allow independent caching policies.
 */
interface MenuApi {
    /**
     * Retrieves the list of top-level menu items in display order.
     * @return [MenuResponse] containing a non-null (possibly empty) ordered list of [MenuItemDto].
     */
    @GET("v1/menu")
    suspend fun getMenu(): MenuResponse
}

/**
 * DTO representing a single navigation menu item shown in UI components.
 */
@kotlinx.serialization.Serializable
data class MenuItemDto(
    /** Stable menu item identifier. */
    val id: String,
    /** Human readable primary title shown to users. */
    val title: String,
    /** Optional shorter or contextual label (may be shown in compact UI variants). */
    val label: String? = null,
    /** Explicit ordering index; lower values appear first. */
    val order: Int = 0,
    /** Optional relative or absolute path to an icon asset. */
    val iconPath: String? = null,
    /** ISO-8601 creation timestamp of this menu entry (empty if unavailable). */
    val createdAt: String = "",
)

/** Wrapper response for the menu endpoint containing ordered navigation items. */
@kotlinx.serialization.Serializable
data class MenuResponse(
    /** Ordered menu items to display; empty list when no navigation entries exist. */
    val items: List<MenuItemDto>,
)

/** Wrapper response containing the list of manifest entries for all articles. */
@kotlinx.serialization.Serializable
data class ManifestResponse(
    /** Collection of article manifest entries. */
    val items: List<ManifestItemDto>,
)

/** Lightweight manifest entry describing an available article and its meta fields. */
@kotlinx.serialization.Serializable
data class ManifestItemDto(
    /** Server stable article id. */
    val id: String,
    /** Primary article title for readers. */
    val title: String,
    /** URL-safe slug used for deep linking or SEO friendly paths. */
    val slug: String,
    /** Monotonically increasing version number for optimistic caching / invalidation. */
    val version: Int = 0,
    /** ISO-8601 timestamp of last modification. */
    val updatedAt: String,
    /** Approximate total word count (content only, excludes HTML tags). */
    val wordCount: Int = 0,
    /** Optional short label (e.g., category badge). */
    val label: String? = null,
    /** Cover image URL for previews/cards (mandatory). */
    val coverUrl: String,
    /** Icon URL representing the article or its category (mandatory). */
    val iconUrl: String,
    /** Mandatory full index URL (pre-rendered HTML root) for fast-path reader loading. */
    val indexUrl: String,
)
