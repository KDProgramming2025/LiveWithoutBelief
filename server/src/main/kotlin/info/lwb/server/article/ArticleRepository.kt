package info.lwb.server.article

import org.springframework.data.jpa.repository.JpaRepository

interface ArticleRepository : JpaRepository<ArticleEntity, String> {
    fun findAllByOrderByUpdatedAtDesc(): List<ArticleEntity>
}

