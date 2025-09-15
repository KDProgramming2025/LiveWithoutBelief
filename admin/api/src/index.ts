import Fastify, { FastifyInstance } from 'fastify'
import cors from '@fastify/cors'
import cookie from '@fastify/cookie'
import multipart from '@fastify/multipart'
import { CONFIG } from './config'
import { registerAuthRoutes } from './routes/auth'
import { registerArticleRoutes } from './routes/articles'
import { registerUserRoutes } from './routes/users'

export function buildServer(): FastifyInstance {
  const server = Fastify({ logger: true })
  server.register(cors, { origin: true, credentials: true })
  server.register(cookie, { secret: process.env.ADMIN_PANEL_COOKIE_SECRET || process.env.PWD_JWT_SECRET || 'CHANGE_ME_DEV' })
  server.register(multipart, { attachFieldsToBody: true, limits: CONFIG.MULTIPART_LIMITS as any })

  server.addHook('onSend', async (_req, reply, payload) => {
    reply.header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
    reply.header('Pragma', 'no-cache')
    reply.header('Expires', '0')
    reply.header('X-Response-Ts', new Date().toISOString())
    return payload as any
  })

  // Best-effort ensure dirs at boot handled inside services/repositories as needed

  // Register route groups
  server.register(async (app) => { await registerAuthRoutes(app) })
  server.register(async (app) => { await registerArticleRoutes(app) })
  server.register(async (app) => { await registerUserRoutes(app) })

  return server
}
