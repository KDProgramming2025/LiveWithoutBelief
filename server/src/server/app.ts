import express, { json } from 'express'
import { articleRouter } from './routes/articles.js'
import { authRouter } from './routes/auth.js'
import { altchaRouter } from './routes/altcha.js'
import { createWebRouter } from './routes/web.js'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export function createServer() {
  const app = express()
  app.use(json())

  app.get('/health', (_req: express.Request, res: express.Response) => res.json({ ok: true }))
  app.use('/v1/articles', articleRouter)
  app.use('/v1/auth', authRouter)
  app.use('/v1/altcha', altchaRouter)
  app.use('/admin', createWebRouter())
  // Serve admin static UI (no frameworks)
  const __dirname = path.dirname(fileURLToPath(import.meta.url))
  const adminRoot = path.resolve(__dirname, '../../../admin/web')
  app.get('/admin/ui', (_req, res) => {
    res.set('Cache-Control', 'no-store')
    res.sendFile(path.join(adminRoot, 'index.html'))
  })
  app.use('/admin/ui', express.static(adminRoot, { etag: false, lastModified: false, maxAge: '1h' }))

  // 404
  app.use((_req: express.Request, res: express.Response) => res.status(404).json({ error: 'not_found' }))
  return app
}
