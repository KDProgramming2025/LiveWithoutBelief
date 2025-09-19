import express, { Router } from 'express'
import { ArticleService } from '../../services/ArticleService.js'

const svc = new ArticleService()

export const articleRouter = Router()

// Public manifest of articles (currently same data as admin list)
articleRouter.get('/manifest', async (_req: express.Request, res: express.Response) => {
  const items = await svc.list()
  res.json({ items })
})

// Fetch an article record by id or slug
articleRouter.get('/:idOrSlug', async (req: express.Request, res: express.Response) => {
  const items = await svc.list()
  const key = req.params.idOrSlug
  const a = items.find(it => it.id === key || it.slug === key)
  if (!a) return res.status(404).json({ error: 'not_found' })
  res.json(a)
})
