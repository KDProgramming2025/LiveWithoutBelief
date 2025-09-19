import express, { Router } from 'express'
import { PgUserRepository } from '../../repositories/UserRepository.js'
import { AuthService } from '../../services/AuthService.js'
import { Pool } from 'pg'
import { verifySolution } from 'altcha-lib'
import { env } from '../config/env.js'
import { OAuth2Client } from 'google-auth-library'

const pool = new Pool() // reads PG* env vars
const users = new PgUserRepository(pool)
const svc = new AuthService(users)

export const authRouter = Router()

// Removed: Google ID token verification; client sends email and server upserts user.

// Register by email (Google flow upsert)
authRouter.post('/register', async (req: express.Request, res: express.Response) => {
  const { email } = req.body ?? {}
  if (!email || typeof email !== 'string') return res.status(400).json({ error: 'bad_request' })
  const { user, created } = await svc.registerByEmail(email)
  res.status(created ? 201 : 200).json({ user })
})

// Verify Google ID token and upsert user by verified email
authRouter.post('/google', async (req: express.Request, res: express.Response) => {
  const { idToken } = req.body ?? {}
  if (!idToken || typeof idToken !== 'string') return res.status(400).json({ error: 'bad_request' })
  try {
    const client = new OAuth2Client()
    const ticket = await client.verifyIdToken({ idToken })
    const payload = ticket.getPayload()
    if (!payload?.email || !payload.aud) return res.status(401).json({ error: 'invalid_token' })
    const aud = Array.isArray(payload.aud) ? payload.aud[0] : String(payload.aud)
    if (env.GOOGLE_CLIENT_IDS.length > 0 && !env.GOOGLE_CLIENT_IDS.includes(aud)) {
      return res.status(401).json({ error: 'invalid_audience' })
    }
    const { user, created } = await svc.registerByEmail(payload.email)
    res.status(created ? 201 : 200).json({ user })
  } catch (e) {
    return res.status(401).json({ error: 'invalid_token' })
  }
})

// Username/password registration (requires official ALTCHA token)
authRouter.post('/pwd/register', async (req: express.Request, res: express.Response) => {
  const { username, password, altcha: token } = req.body ?? {}
  if (!username || !password || !token) return res.status(400).json({ error: 'bad_request' })
  try {
    const ok = await verifySolution(String(token), env.ALTCHA_SECRET)
    if (!ok) {
      return res.status(400).set('X-Auth-Reason', 'altcha_failed').json({ error: 'altcha_failed' })
    }
  } catch (e) {
    return res.status(400).set('X-Auth-Reason', 'altcha_error').json({ error: 'altcha_failed' })
  }
  const user = await svc.passwordRegister(String(username), String(password))
  if (!user) return res.status(409).json({ error: 'user_exists' })
  res.json({ user })
})

// Username/password login (no ALTCHA)
authRouter.post('/pwd/login', async (req: express.Request, res: express.Response) => {
  const { username, password } = req.body ?? {}
  if (!username || !password) return res.status(400).json({ error: 'bad_request' })
  const user = await svc.passwordLogin(String(username), String(password))
  if (!user) return res.status(401).json({ error: 'invalid_credentials' })
  res.json({ user })
})
