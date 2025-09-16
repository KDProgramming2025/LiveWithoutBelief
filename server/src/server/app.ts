import express, { json } from 'express'
import { articleRouter } from './routes/articles.js'
import { authRouter } from './routes/auth.js'
import { altchaRouter } from './routes/altcha.js'
import { createWebRouter } from './routes/web.js'

export function createServer() {
  const app = express()
  app.use(json())

  app.get('/health', (_req: express.Request, res: express.Response) => res.json({ ok: true }))
  app.use('/v1/articles', articleRouter)
  app.use('/v1/auth', authRouter)
  app.use('/v1/altcha', altchaRouter)
  app.use('/admin', createWebRouter())

  // 404
  app.use((_req: express.Request, res: express.Response) => res.status(404).json({ error: 'not_found' }))
  return app
}
