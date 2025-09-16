import express, { Router } from 'express'
import { InMemoryArticleRepository } from '../../repositories/ArticleRepository.js'
import { ArticleService } from '../../services/ArticleService.js'

const repo = new InMemoryArticleRepository()
const svc = new ArticleService(repo)

export const articleRouter = Router()

articleRouter.get('/manifest', async (_req: express.Request, res: express.Response) => {
  const data = await svc.manifest()
  res.json(data)
})

articleRouter.get('/:id', async (req: express.Request, res: express.Response) => {
  const a = await svc.article(req.params.id)
  if (!a) return res.status(404).json({ error: 'not_found' })
  res.json(a)
})
