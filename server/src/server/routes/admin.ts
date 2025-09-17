import express from 'express'
import { AdminAuthService } from '../../services/AdminAuthService.js'
import { AdminUserService } from '../../services/AdminUserService.js'
import { Pool } from 'pg'
import multer from 'multer'
import fs from 'node:fs'
import path from 'node:path'
import { MenuService } from '../../services/MenuService.js'

export const adminRouter = express.Router()
const auth = new AdminAuthService()
const pool = new Pool()
const userSvc = new AdminUserService(pool)
const menuSvc = new MenuService(pool)
const uploadDir = path.resolve('/var/www/LWB/uploads')
fs.mkdirSync(uploadDir, { recursive: true })
const upload = multer({ dest: uploadDir })

adminRouter.post('/login', async (req, res) => {
  const { username, password } = req.body || {}
  if (!username || !password) return res.status(400).json({ error: 'bad_request' })
  const ok = await auth.verifyPassword(username, password)
  if (!ok) return res.status(401).json({ error: 'unauthorized' })
  const token = auth.issueToken(username)
  res.json({ token })
})

export function requireAdmin(req: express.Request, res: express.Response, next: express.NextFunction) {
  const hdr = req.header('Authorization') || ''
  const m = hdr.match(/^Bearer\s+(.+)$/i)
  if (!m) return res.status(401).json({ error: 'unauthorized' })
  const session = auth.verifyToken(m[1])
  if (!session) return res.status(401).json({ error: 'unauthorized' })
  ;(req as any).admin = session
  next()
}

// Users: list with optional search q, simple paging
adminRouter.get('/users', (req, res) => {
  const q = typeof req.query.q === 'string' ? req.query.q : undefined
  const limit = Math.min(100, Math.max(1, Number(req.query.limit ?? 50)))
  const offset = Math.max(0, Number(req.query.offset ?? 0))
  const handler = async () => {
    const [total, items] = await Promise.all([
      userSvc.countUsers(),
      userSvc.listUsers(q, limit, offset),
    ])
    res.json({ total, items, limit, offset })
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Users: delete
adminRouter.delete('/users/:id', (req, res) => {
  const handler = async () => {
    const ok = await userSvc.deleteUser(req.params.id)
    res.status(ok ? 204 : 404).end()
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Menu: list
adminRouter.get('/menu', (req, res) => {
  const handler = async () => {
    const items = await menuSvc.list()
    res.json({ items })
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Menu: create (multipart form)
adminRouter.post('/menu', upload.single('icon'), (req, res) => {
  const handler = async () => {
    const { title, label, order } = req.body || {}
    if (!title || typeof title !== 'string') return res.status(400).json({ error: 'bad_request' })
    const iconPath = req.file ? `/uploads/${path.basename(req.file.path)}` : null
    const item = await menuSvc.create({ title, label: label ?? null, order: Number(order ?? 0), iconPath })
    res.status(201).json({ item })
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Menu: delete
adminRouter.delete('/menu/:id', (req, res) => {
  const handler = async () => {
    const ok = await menuSvc.delete(req.params.id)
    res.status(ok ? 204 : 404).end()
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})
