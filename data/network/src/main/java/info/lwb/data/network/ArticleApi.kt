package info.lwb.data.network

import retrofit2.http.GET

interface ArticleApi {
    @GET("v1/articles/manifest")
    suspend fun getManifest(): List<ManifestItemDto>
}

data class ManifestItemDto(
    val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    val updatedAt: String,
    val wordCount: Int,
)

