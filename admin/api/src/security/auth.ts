import jwt from 'jsonwebtoken'
import { CONFIG } from '../config'
import { FastifyRequest } from 'fastify'
import type { AdminJwt } from '../types'

export function setSession(reply: any, username: string) {
  const token = jwt.sign({ sub: username, typ: 'admin' }, CONFIG.JWT_SECRET, { expiresIn: '7d' })
  reply.setCookie('lwb_admin', token, { httpOnly: true, sameSite: 'lax', secure: true, path: '/', maxAge: 7 * 24 * 3600 })
}

export function clearSession(reply: any) { reply.clearCookie('lwb_admin', { path: '/' }) }

export function requireAdmin(req: FastifyRequest, reply: any): boolean {
  const token = (req.cookies as any)?.['lwb_admin']
  if (!token) { reply.code(401).send({ error: 'unauthorized' }); return false }
  try {
    const decoded = jwt.verify(token, CONFIG.JWT_SECRET) as AdminJwt
    if (decoded.typ !== 'admin') throw new Error('wrong_type')
    return true
  } catch {
    reply.code(401).send({ error: 'unauthorized' })
    return false
  }
}
