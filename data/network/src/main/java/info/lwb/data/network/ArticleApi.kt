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

    /**
     * Fetches a single article payload by its server identifier.
     *
     * @param id Stable unique server-side article identifier (path segment).
     * @return [ArticleDto] including core textual content, sections, media references and integrity signature.
     */
    @GET("v1/articles/{id}")
    suspend fun getArticle(@retrofit2.http.Path("id") id: String): ArticleDto
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
    /** Optional cover image URL for previews/cards. */
    val coverUrl: String? = null,
    /** Optional icon URL representing the article or its category. */
    val iconUrl: String? = null,
)

/**
 * Represents a logical section of an article (heading, paragraph, media reference, etc.).
 * Either `text` or `html` can supply the body content. `mediaRefId` links to a [MediaDto] when kind denotes media.
 */
data class SectionDto(
    /** Display ordering index inside the article. */
    val order: Int,
    /** Logical kind (e.g., heading, paragraph, image, code). */
    val kind: String,
    /** Optional hierarchical level (e.g., heading level). */
    val level: Int? = null,
    /** Plain text content if available (may be null when only HTML exists). */
    val text: String? = null,
    /** Raw HTML fragment (sanitized server-side) alternative to plain text. */
    val html: String? = null,
    /** Optional reference id pointing to a media element within the article's media list. */
    val mediaRefId: String? = null,
)

/**
 * Metadata describing an associated media asset referenced by article sections.
 */
data class MediaDto(
    /** Stable media identifier referenced by sections. */
    val id: String,
    /** Media type classification (image, audio, video, etc.). */
    val type: String,
    /** Original file name if provided. */
    val filename: String? = null,
    /** MIME content type (e.g., image/png). */
    val contentType: String? = null,
    /** Resolved absolute or relative source URL for the media resource. */
    val src: String? = null,
    /** Optional integrity checksum (hash string) for validation/caching. */
    val checksum: String? = null,
)

/**
 * Full article payload including textual content, structural sections, and referenced media.
 */
data class ArticleDto(
    /** Server stable article id. */
    val id: String,
    /** Slug for constructing shareable links. */
    val slug: String,
    /** Reader facing primary title. */
    val title: String,
    /** Version number used to detect updates. */
    val version: Int,
    /** Total word count of the article body content. */
    val wordCount: Int,
    /** ISO-8601 last update timestamp. */
    val updatedAt: String,
    /** Integrity checksum over canonical textual representation. */
    val checksum: String,
    /** Optional cryptographic or server signature for verification. */
    val signature: String? = null,
    /** Full article HTML (optional when only plain text is supplied). */
    val html: String? = null,
    /** Plain text rendition (optional, may be derived). */
    val text: String? = null,
    /** Optional canonical URL of the exported static HTML index for this article. */
    val indexUrl: String? = null,
    /** Ordered structural sections. */
    val sections: List<SectionDto> = emptyList(),
    /** Associated media assets referenced by sections. */
    val media: List<MediaDto> = emptyList(),
)
