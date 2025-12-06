import express from 'express'
import { AdminAuthService } from '../../services/AdminAuthService.js'
import { AdminUserService } from '../../services/AdminUserService.js'
import { Pool } from 'pg'
import multer from 'multer'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import Busboy from 'busboy'
import { MenuService } from '../../services/MenuService.js'
import { ArticleService } from '../../services/ArticleService.js'
import { uploadProgress } from '../../services/UploadProgressService.js'

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

adminRouter.get('/progress/:id', (req, res) => {
  const { id } = req.params
  console.log(`[SSE] Client connected for uploadId=${id}`)
  res.setHeader('Content-Type', 'text/event-stream')
  res.setHeader('Cache-Control', 'no-cache')
  res.setHeader('Connection', 'keep-alive')

  const onProgress = (uploadId: string, status: any) => {
    if (uploadId === id) {
      // console.log(`[SSE] Sending progress for ${id}: ${status.loaded}/${status.total}`)
      res.write(`data: ${JSON.stringify(status)}\n\n`)
      if (status.status === 'completed' || status.status === 'error') {
        res.end()
        uploadProgress.cleanup(id)
      }
    }
  }

  uploadProgress.on('progress', onProgress)

  const current = uploadProgress.get(id)
  if (current) {
    console.log(`[SSE] Sending initial state for ${id}`)
    res.write(`data: ${JSON.stringify(current)}\n\n`)
  } else {
    console.log(`[SSE] No active upload found for ${id}`)
  }

  req.on('close', () => {
    console.log(`[SSE] Client disconnected for ${id}`)
    uploadProgress.off('progress', onProgress)
  })
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
adminRouter.post('/articles', (req, res) => {
  const handler = async () => {
    const uploadId = req.query.uploadId as string
    // If no uploadId, fallback to old behavior? No, let's enforce it or just not track progress.
    // But we need busboy anyway to avoid multer buffering if we want progress.
    
    const busboy = Busboy({ headers: req.headers })
    const fields: any = {}
    const files: any = {}
    const tmpDir = '/var/www/LWB/tmp'
    fs.mkdirSync(tmpDir, { recursive: true })

    let totalBytes = Number(req.headers['content-length']) || 0
    console.log(`[Upload] Starting uploadId=${uploadId} total=${totalBytes}`)
    if (uploadId) uploadProgress.init(uploadId, totalBytes)
    let loadedBytes = 0

    busboy.on('file', (name, file, info) => {
      console.log(`[Upload] File start: ${name} ${info.filename}`)
      const tmpPath = path.join(tmpDir, `upload_${crypto.randomBytes(8).toString('hex')}_${info.filename}`)
      const writeStream = fs.createWriteStream(tmpPath)
      
      file.on('data', (data) => {
        loadedBytes += data.length
        if (uploadId) uploadProgress.update(uploadId, loadedBytes)
      })

      file.pipe(writeStream)
      
      const filePromise = new Promise((resolve, reject) => {
          writeStream.on('finish', () => {
            console.log(`[Upload] File finish: ${name}`)
            resolve({ path: tmpPath, originalname: info.filename })
          })
          writeStream.on('error', reject)
      })
      files[name] = filePromise
    })

    busboy.on('field', (name, val) => {
      fields[name] = val
    })

    busboy.on('error', (err: any) => {
      console.error('Busboy error:', err)
      if (uploadId) uploadProgress.setStatus(uploadId, 'error', 'Upload failed')
      // Ensure we don't send double response if finish already fired (unlikely but possible)
      if (!res.headersSent) res.status(400).json({ error: 'upload_failed' })
    })

    busboy.on('finish', async () => {
      try {
        if (uploadId) uploadProgress.setStatus(uploadId, 'processing', 'Processing files...')
        
        const resolvedFiles: any = {}
        for (const key of Object.keys(files)) {
          resolvedFiles[key] = await files[key]
        }

        const { title, label, order } = fields
        const docxTmpPath = resolvedFiles.docx?.path
        const coverPath = resolvedFiles.cover?.path
        const iconPath = resolvedFiles.icon?.path

        if (!title || !docxTmpPath) {
           return res.status(400).json({ error: 'bad_request' })
        }

        const item = await articleSvc.createOrReplace({ 
          title, 
          label: label ?? null, 
          order: Number(order ?? 0), 
          docxTmpPath, 
          coverPath, 
          iconPath 
        })
        
        if (uploadId) uploadProgress.setStatus(uploadId, 'completed')
        res.status(201).json({ item })
      } catch (err) {
        console.error(err)
        if (uploadId) uploadProgress.setStatus(uploadId, 'error', 'Server error')
        res.status(500).json({ error: 'server_error' })
      }
    })

    req.pipe(busboy)
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
const articleUpload = multer({ dest: path.resolve('/var/www/LWB/tmp') })
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
