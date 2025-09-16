import { ArticleRepository } from '../repositories/ArticleRepository.js'

export class ArticleService {
  constructor(private readonly repo: ArticleRepository) {}
  manifest() { return this.repo.all() }
  article(id: string) { return this.repo.byId(id) }
}
