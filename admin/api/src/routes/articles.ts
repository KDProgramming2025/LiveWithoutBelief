import { FastifyInstance, FastifyRequest } from 'fastify'
import { requireAdmin } from '../security/auth'
import { ArticleService } from '../services/articleService'
import { readPartBuffer } from '../utils/multipart'

export async function registerArticleRoutes(server: FastifyInstance) {
  const svc = new ArticleService()

  server.get('/v1/admin/articles', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const items = await svc.list()
    return reply.code(200).send({ items })
  })

  server.post('/v1/admin/articles/:id/move', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const id = (req.params as { id: string }).id
    const dir = ((req.body ?? {}) as { direction?: 'up'|'down' }).direction
    if (dir !== 'up' && dir !== 'down') return reply.code(400).send({ error: 'invalid_direction' })
    try { await svc.move(id, dir); return { ok: true } } catch (e: any) {
      if (e.statusCode) return reply.code(e.statusCode).send({ error: e.message })
      throw e
    }
  })

  server.post('/v1/admin/articles/:id/edit', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const id = (req.params as { id: string }).id
    const body = (req as FastifyRequest & { body?: any }).body || {}
    const title = typeof body.title === 'string' ? body.title : (body.title && typeof body.title === 'object' && typeof body.title.value !== 'undefined') ? String(body.title.value) : undefined

    let saved: Array<any> = []
    try { const saver: any = (req as any).saveRequestFiles ? (req as any) : null; saved = saver ? await saver.saveRequestFiles() : [] } catch (e) { (req as any).log?.warn?.({ err: e }, 'edit.saveRequestFiles failed') }
    try {
      const { item, warnings } = await svc.edit(id, { title, files: saved, logger: (req as any).log })
      return { ok: true, item, warnings }
    } catch (e: any) {
      if (e.statusCode) return reply.code(e.statusCode).send({ error: e.message })
      throw e
    } finally {
      // Cleanup in service already; nothing here
    }
  })

  server.post('/v1/admin/articles/upload', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const body = (req as FastifyRequest & { body?: any }).body || {}
    let titleInput: string | undefined = undefined
    if (typeof body.title === 'string') titleInput = body.title.trim()
    else if (body.title && typeof body.title === 'object' && typeof body.title.value !== 'undefined') titleInput = String(body.title.value).trim()
    const replaceFlag: boolean = ((): boolean => {
      const r = (body.replace ?? body.overwrite ?? body.force) as any
      if (typeof r === 'string') return /^true|1|yes$/i.test(r)
      if (typeof r === 'boolean') return r
      if (r && typeof r === 'object' && typeof r.value !== 'undefined') return /^true|1|yes$/i.test(String(r.value))
      return false
    })()

    let upName: string | undefined
    let docx: Buffer | undefined
    let coverBuf: Buffer | undefined; let coverOrig: string | undefined; let coverMime: string | undefined
    let iconBuf: Buffer | undefined; let iconOrig: string | undefined; let iconMime: string | undefined

    const fileDoc = body.docx || body.file || body.document
    if (fileDoc) {
      const r = await readPartBuffer(fileDoc)
      docx = r.buffer; upName = r.filename || upName
    }
    if (body.cover && typeof body.cover === 'object') {
      const r = await readPartBuffer(body.cover)
      if (r.buffer?.length) { coverBuf = r.buffer; coverOrig = r.filename; coverMime = r.mimetype }
    }
    if (body.icon && typeof body.icon === 'object') {
      const r = await readPartBuffer(body.icon)
      if (r.buffer?.length) { iconBuf = r.buffer; iconOrig = r.filename; iconMime = r.mimetype }
    }

    if (!docx || docx.length < 4) {
      try {
        const saver: any = (req as any).saveRequestFiles ? (req as any) : null
        if (saver) {
          const saved: Array<any> = await saver.saveRequestFiles()
          for (const f of saved) {
            try {
              const buf = await (await import('fs/promises')).readFile(f.filepath)
              if ((f.fieldname === 'docx' || f.fieldname === 'file' || f.fieldname === 'document') && (!docx || docx.length < 4)) {
                docx = buf; upName = f.filename || upName
              } else if (f.fieldname === 'cover' && !coverBuf) {
                coverBuf = buf; coverOrig = f.filename; coverMime = f.mimetype
              } else if (f.fieldname === 'icon' && !iconBuf) {
                iconBuf = buf; iconOrig = f.filename; iconMime = f.mimetype
              }
            } finally {
              try { await (await import('fs/promises')).unlink(f.filepath) } catch (e) { req.log.warn({ err: e, file: f.filepath }, 'cleanup tmp file failed') }
            }
          }
        }
      } catch (e) {
        req.log.warn({ err: e }, 'saveRequestFiles fallback failed')
      }
    }

    if (!docx || docx.length < 4) {
      return reply.code(400).send({ error: 'invalid_docx' })
    }

    try {
      const { item } = await svc.upload({
        title: titleInput,
        replace: replaceFlag,
        docx: docx!,
        upName,
        cover: coverBuf ? { buf: coverBuf, filename: coverOrig, mime: coverMime } : undefined,
        icon: iconBuf ? { buf: iconBuf, filename: iconOrig, mime: iconMime } : undefined,
      })
      return reply.code(200).send({ ok: true, item })
    } catch (e: any) {
      if (e.statusCode === 409) return reply.code(409).send({ ok: false, error: 'article_exists', id: e?.details?.id, message: 'Article already exists. Resubmit with replace=true to overwrite.' })
      if (e.statusCode) return reply.code(e.statusCode).send({ ok: false, error: e.message })
      throw e
    }
  })

  server.post('/v1/admin/articles/reindex', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const count = await svc.reindex()
    return { ok: true, count }
  })
}
