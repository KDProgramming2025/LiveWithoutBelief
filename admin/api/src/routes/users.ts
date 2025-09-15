import { FastifyInstance } from 'fastify'
import { requireAdmin } from '../security/auth'
import { UserService } from '../services/userService'

export async function registerUserRoutes(server: FastifyInstance) {
  const svc = new UserService()

  server.get('/v1/admin/users/summary', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    try {
      const { total } = await svc.summary()
      return { total }
    } catch (e) {
      server.log.error({ err: e }, 'users.summary failed')
      return reply.code(500).send({ error: 'server_error' })
    }
  })

  server.get('/v1/admin/users/search', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const q = (req.query as { q?: string; limit?: string; offset?: string }).q || ''
    const limit = Math.max(1, Math.min(100, Number((req.query as any).limit) || 20))
    const offset = Math.max(0, Number((req.query as any).offset) || 0)
    try {
      const r = await svc.search(q, limit, offset)
      return r
    } catch (e) {
      server.log.error({ err: e }, 'users.search failed')
      return reply.code(500).send({ error: 'server_error' })
    }
  })

  server.delete('/v1/admin/users/:id', async (req, reply) => {
    if (!requireAdmin(req, reply)) return
    const id = (req.params as { id: string }).id
    try {
      const r = await svc.delete(id)
      return r
    } catch (e: any) {
      if (e.statusCode) return reply.code(e.statusCode).send({ ok: false, error: e.message })
      server.log.error({ err: e, id }, 'users.delete failed')
      return reply.code(500).send({ ok: false, error: 'server_error' })
    }
  })
}
