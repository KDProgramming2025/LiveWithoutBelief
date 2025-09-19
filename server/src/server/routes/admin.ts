import express from 'express'
import { AdminAuthService } from '../../services/AdminAuthService.js'
import { AdminUserService } from '../../services/AdminUserService.js'
import { Pool } from 'pg'
import multer from 'multer'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { MenuService } from '../../services/MenuService.js'
import { ArticleService } from '../../services/ArticleService.js'

export const adminRouter = express.Router()
const auth = new AdminAuthService()
const pool = new Pool()
const userSvc = new AdminUserService(pool)
const menuSvc = new MenuService(pool)
const articleSvc = new ArticleService()
const uploadDir = path.resolve('/var/www/LWB/uploads')
fs.mkdirSync(uploadDir, { recursive: true })

const IMAGE_MIME_TO_EXT: Record<string, string> = {
  'image/png': '.png',
  'image/jpeg': '.jpg',
  'image/jpg': '.jpg',
  'image/gif': '.gif',
  'image/webp': '.webp',
  'image/svg+xml': '.svg',
  'image/avif': '.avif'
}

const storage = multer.diskStorage({
  destination: (_req: any, _file: any, cb: any) => cb(null, uploadDir),
  filename: (_req: any, file: any, cb: any) => {
    const extFromMime = IMAGE_MIME_TO_EXT[file.mimetype] ?? ''
    const extFromName = path.extname(file.originalname || '')
    const ext = extFromMime || extFromName || ''
    const base = crypto.randomBytes(16).toString('hex')
    cb(null, `${base}${ext}`)
  }
})

const upload = multer({
  storage,
  fileFilter: (_req: any, file: any, cb: any) => {
    if (file.mimetype && file.mimetype.startsWith('image/')) return cb(null, true)
    cb(new Error('unsupported_file_type'))
  },
  limits: { fileSize: 5 * 1024 * 1024 }
})

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

// Articles: list (from JSON manifest)
adminRouter.get('/articles', (req, res) => {
  const handler = async () => {
    const items = await articleSvc.list()
    res.json({ items })
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Articles: upload (multipart form) — fields: title, label, order; files: docx, cover, icon
const articleUpload = multer({ dest: path.resolve('/var/www/LWB/tmp') })
adminRouter.post('/articles', articleUpload.fields([
  { name: 'docx', maxCount: 1 },
  { name: 'cover', maxCount: 1 },
  { name: 'icon', maxCount: 1 },
]), (req, res) => {
  const handler = async () => {
    const { title, label, order } = req.body || {}
    const anyReq: any = req
    if (!title || !anyReq.files || !anyReq.files.docx || !anyReq.files.docx[0]) {
      return res.status(400).json({ error: 'bad_request' })
    }
    const docxTmpPath = anyReq.files.docx[0].path
    const coverPath = anyReq.files.cover?.[0]?.path
    const iconPath = anyReq.files.icon?.[0]?.path
    const item = await articleSvc.createOrReplace({ title, label: label ?? null, order: Number(order ?? 0), docxTmpPath, coverPath, iconPath })
    res.status(201).json({ item })
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Articles: delete by id (or slug)
adminRouter.delete('/articles/:id', (req, res) => {
  const handler = async () => {
    const ok = await articleSvc.delete(req.params.id)
    res.status(ok ? 204 : 404).end()
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Articles: move up/down
adminRouter.post('/articles/:id/move', (req, res) => {
  const handler = async () => {
    const dir = (req.body?.direction === 'up' || req.body?.direction === 'down') ? req.body.direction : undefined
    if (!dir) return res.status(400).json({ error: 'bad_request' })
    const ok = await articleSvc.move(req.params.id, dir)
    res.status(ok ? 204 : 404).end()
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Articles: partial update (multipart fields optional)
adminRouter.patch('/articles/:id', articleUpload.fields([
  { name: 'docx', maxCount: 1 },
  { name: 'cover', maxCount: 1 },
  { name: 'icon', maxCount: 1 },
]), (req, res) => {
  const handler = async () => {
    const { title, label, order } = req.body || {}
    const anyReq: any = req
    const docxTmpPath = anyReq.files?.docx?.[0]?.path
    const coverPath = anyReq.files?.cover?.[0]?.path
    const iconPath = anyReq.files?.icon?.[0]?.path
    const payload: any = {}
    if (typeof title === 'string' && title.trim() !== '') payload.title = title
    if (typeof label === 'string') payload.label = label
    if (order !== undefined && order !== null && order !== '' && Number.isFinite(Number(order))) payload.order = Number(order)
    if (docxTmpPath) payload.docxTmpPath = docxTmpPath
    if (coverPath) payload.coverPath = coverPath
    if (iconPath) payload.iconPath = iconPath
    const updated = await articleSvc.update(req.params.id, payload)
    if (!updated) return res.status(404).json({ error: 'not_found' })
    res.json({ item: updated })
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Menu: update title/label/order (JSON)
adminRouter.patch('/menu/:id', (req, res) => {
  const handler = async () => {
    const { title, label, order } = req.body || {}
    const updated = await menuSvc.update(req.params.id, { title, label, order: Number.isFinite(order) ? Number(order) : undefined })
    if (!updated) return res.status(404).json({ error: 'not_found' })
    res.json({ item: updated })
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Menu: move up/down
adminRouter.post('/menu/:id/move', (req, res) => {
  const handler = async () => {
    const dir = (req.body?.direction === 'up' || req.body?.direction === 'down') ? req.body.direction : undefined
    if (!dir) return res.status(400).json({ error: 'bad_request' })
    const ok = await menuSvc.move(req.params.id, dir)
    res.status(ok ? 204 : 404).end()
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})

// Menu: update icon (multipart)
adminRouter.post('/menu/:id/icon', upload.single('icon'), (req, res) => {
  const handler = async () => {
    if (!req.file) return res.status(400).json({ error: 'bad_request' })
    const iconPath = `/uploads/${path.basename(req.file.path)}`
    const ok = await menuSvc.updateIcon(req.params.id, iconPath)
    res.status(ok ? 204 : 404).end()
  }
  return requireAdmin(req, res, (err?: any) => err ? res.status(401).end() : handler())
})
