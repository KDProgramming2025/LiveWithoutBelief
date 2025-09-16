import { ArticleDto, ManifestItemDto } from '../domain/article.js'

export interface ArticleRepository {
  all(): Promise<ManifestItemDto[]>
  byId(id: string): Promise<ArticleDto | null>
}

const now = new Date().toISOString()
const articles: ArticleDto[] = [
  {
    id: 'a1',
    slug: 'welcome',
    title: 'Welcome to Live Without Belief',
    version: 1,
    wordCount: 120,
    updatedAt: now,
    checksum: 'abc123',
    html: '<h1>Welcome</h1><p>This is a sample article.</p>',
    sections: [
      { order: 1, kind: 'h1', level: 1, text: 'Welcome' },
      { order: 2, kind: 'p', text: 'This is a sample article.' },
    ],
    media: [],
  },
]

export class InMemoryArticleRepository implements ArticleRepository {
  async all(): Promise<ManifestItemDto[]> {
    return articles.map(a => ({
      id: a.id,
      title: a.title,
      slug: a.slug,
      version: a.version,
      updatedAt: a.updatedAt,
      wordCount: a.wordCount,
    }))
  }
  async byId(id: string): Promise<ArticleDto | null> {
    return articles.find(a => a.id === id) ?? null
  }
}
