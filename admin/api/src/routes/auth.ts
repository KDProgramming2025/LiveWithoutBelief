import { FastifyInstance, FastifyRequest } from 'fastify'
import { CONFIG } from '../config'
import { clearSession, setSession } from '../security/auth'
import jwt from 'jsonwebtoken'

export async function registerAuthRoutes(server: FastifyInstance) {
  server.post('/v1/admin/login', async (req, reply) => {
    const body = (req.body ?? {}) as { username?: string; password?: string }
    const { username, password } = body
    if (!username || !password) return reply.code(400).send({ error: 'invalid_body' })
    if (username !== CONFIG.ADMIN_USER || password !== CONFIG.ADMIN_PASS) return reply.code(401).send({ error: 'invalid_credentials' })
    setSession(reply, username)
    return { ok: true, username }
  })

  server.post('/v1/admin/logout', async (_req, reply) => { clearSession(reply); return { ok: true } })

  server.get('/v1/admin/session', async (req, reply) => {
    const token = (req.cookies as any)?.['lwb_admin']
    if (!token) return reply.code(200).send({ authenticated: false })
    try { const d = jwt.verify(token, CONFIG.JWT_SECRET) as any; return reply.code(200).send({ authenticated: true, username: d.sub }) } catch { return reply.code(200).send({ authenticated: false }) }
  })
}
