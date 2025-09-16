import { FastifyInstance, FastifyRequest } from 'fastify'
import { requireAdmin } from '../security/auth'
import { MenuService } from '../services/menuService'
import { readPartBuffer } from '../utils/multipart'

export async function registerMenuRoutes(server: FastifyInstance) {
  const svc = new MenuService()

  server.get('/v1/admin/menu', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const items = await svc.list()
    return reply.code(200).send({ items })
  })

  server.post('/v1/admin/menu', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const body = (req as FastifyRequest & { body?: any }).body || {}
    const title = typeof body.title === 'string' ? body.title : (body.title?.value != null ? String(body.title.value) : '')
    const label = typeof body.label === 'string' ? body.label : (body.label?.value != null ? String(body.label.value) : '')
    let order: number | undefined
    if (typeof body.order === 'string') { const n = Number(body.order); if (!Number.isNaN(n)) order = n }
    else if (typeof body.order === 'number') order = body.order

    let iconBuf: Buffer | undefined; let iconOrig: string | undefined; let iconMime: string | undefined
    if (body.icon && typeof body.icon === 'object') {
      const r = await readPartBuffer(body.icon)
      if (r.buffer?.length) { iconBuf = r.buffer; iconOrig = r.filename; iconMime = r.mimetype }
    }
    // Fallback: if no icon buffer was captured, try saving to temp files (proxy/env variations may exhaust streams)
    if (!iconBuf || iconBuf.length === 0) {
      try {
        const saver: any = (req as any).saveRequestFiles ? (req as any) : null
        if (saver) {
          const saved: Array<any> = await saver.saveRequestFiles()
          for (const f of saved) {
            try {
              if (f.fieldname === 'icon' && (!iconBuf || iconBuf.length === 0)) {
                const buf = await (await import('fs/promises')).readFile(f.filepath)
                iconBuf = buf; iconOrig = f.filename; iconMime = f.mimetype
              }
            } finally {
              try { await (await import('fs/promises')).unlink(f.filepath) } catch (e) { (req as any).log?.warn?.({ err: e, file: f.filepath }, 'menu.add cleanup tmp file failed') }
            }
          }
        }
      } catch (e) {
        (req as any).log?.warn?.({ err: e }, 'menu.add saveRequestFiles fallback failed')
      }
    }

    const item = await svc.add({ title, label, order, icon: iconBuf ? { buf: iconBuf, filename: iconOrig, mime: iconMime } : undefined })
    return reply.code(200).send({ ok: true, item })
  })

  server.post('/v1/admin/menu/:id/move', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const id = (req.params as { id: string }).id
    const dir = ((req.body ?? {}) as { direction?: 'up'|'down' }).direction
    if (dir !== 'up' && dir !== 'down') return reply.code(400).send({ error: 'invalid_direction' })
    await svc.move(id, dir)
    return { ok: true }
  })

  server.post('/v1/admin/menu/:id/edit', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const id = (req.params as { id: string }).id
    const body = (req as FastifyRequest & { body?: any }).body || {}
    const out: { title?: string; label?: string; icon?: { buf: Buffer; filename?: string; mime?: string } } = {}
    if (typeof body.title === 'string') out.title = body.title
    else if (body.title?.value != null) out.title = String(body.title.value)
    if (typeof body.label === 'string') out.label = body.label
    else if (body.label?.value != null) out.label = String(body.label.value)
    if (body.icon && typeof body.icon === 'object') {
      const r = await readPartBuffer(body.icon)
      if (r.buffer?.length) out.icon = { buf: r.buffer, filename: r.filename, mime: r.mimetype }
    }
    // Fallback: if icon still missing or zero-length, try saveRequestFiles to capture buffered upload
    if (!out.icon || !out.icon.buf?.length) {
      try {
        const saver: any = (req as any).saveRequestFiles ? (req as any) : null
        if (saver) {
          const saved: Array<any> = await saver.saveRequestFiles()
          for (const f of saved) {
            try {
              if (f.fieldname === 'icon' && (!out.icon || !out.icon.buf?.length)) {
                const buf = await (await import('fs/promises')).readFile(f.filepath)
                out.icon = { buf, filename: f.filename, mime: f.mimetype }
              }
            } finally {
              try { await (await import('fs/promises')).unlink(f.filepath) } catch (e) { (req as any).log?.warn?.({ err: e, file: f.filepath }, 'menu.edit cleanup tmp file failed') }
            }
          }
        }
      } catch (e) {
        (req as any).log?.warn?.({ err: e }, 'menu.edit saveRequestFiles fallback failed')
      }
    }
    const item = await svc.edit(id, out)
    return reply.code(200).send({ ok: true, item })
  })

  server.delete('/v1/admin/menu/:id', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const id = (req.params as { id: string }).id
    await svc.remove(id)
    return { ok: true }
  })
}
