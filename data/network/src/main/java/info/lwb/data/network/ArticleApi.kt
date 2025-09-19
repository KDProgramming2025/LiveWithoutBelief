/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.network

import retrofit2.http.GET

interface ArticleApi {
    @GET("v1/articles/manifest")
    suspend fun getManifest(): List<ManifestItemDto>

    @GET("v1/articles/{id}")
    suspend fun getArticle(@retrofit2.http.Path("id") id: String): ArticleDto
}

// Public Menu API
interface MenuApi {
    @GET("v1/menu")
    suspend fun getMenu(): MenuResponse
}

@kotlinx.serialization.Serializable
data class MenuItemDto(
    val id: String,
    val title: String,
    val label: String? = null,
    val order: Int = 0,
    val iconPath: String? = null,
    val createdAt: String = "",
)

@kotlinx.serialization.Serializable
data class MenuResponse(val items: List<MenuItemDto>)

data class ManifestItemDto(
    val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    val updatedAt: String,
    val wordCount: Int,
)

data class SectionDto(
    val order: Int,
    val kind: String,
    val level: Int? = null,
    val text: String? = null,
    val html: String? = null,
    val mediaRefId: String? = null,
)

data class MediaDto(
    val id: String,
    val type: String,
    val filename: String? = null,
    val contentType: String? = null,
    val src: String? = null,
    val checksum: String? = null,
)

data class ArticleDto(
    val id: String,
    val slug: String,
    val title: String,
    val version: Int,
    val wordCount: Int,
    val updatedAt: String,
    val checksum: String,
    val signature: String? = null,
    val html: String? = null,
    val text: String? = null,
    val sections: List<SectionDto> = emptyList(),
    val media: List<MediaDto> = emptyList(),
)
