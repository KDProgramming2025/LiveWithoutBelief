package info.lwb.server.article

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/articles")
class ArticleController(
    private val repository: ArticleRepository
) {
    @GetMapping("/manifest")
    fun manifest(): List<ManifestItemDto> = repository.findAllByOrderByUpdatedAtDesc().map {
        ManifestItemDto(
            id = it.id,
            title = it.title,
            slug = it.slug,
            version = it.version,
            updatedAt = it.updatedAt,
            wordCount = it.wordCount
        )
    }
}

data class ManifestItemDto(
    val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    val updatedAt: String,
    val wordCount: Int,
)

