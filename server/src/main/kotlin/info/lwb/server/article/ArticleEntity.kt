package info.lwb.server.article

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "articles")
class ArticleEntity(
    @Id
    val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    @Column(name = "updated_at")
    val updatedAt: String,
    @Column(name = "word_count")
    val wordCount: Int,
)

