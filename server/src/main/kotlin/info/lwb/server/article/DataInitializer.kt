package info.lwb.server.article

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class DataInitializer(private val repo: ArticleRepository) {
    @PostConstruct
    fun seed() {
        if (repo.count() == 0L) {
            repeat(3) { idx ->
                repo.save(
                    ArticleEntity(
                        id = UUID.randomUUID().toString(),
                        title = "Sample Article ${idx + 1}",
                        slug = "sample-${idx + 1}",
                        version = 1,
                        updatedAt = Instant.now().toString(),
                        wordCount = 500 + idx * 100
                    )
                )
            }
        }
    }
}

