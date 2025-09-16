import express, { Router } from 'express'
import { PgUserRepository } from '../../repositories/UserRepository.js'
import { AuthService } from '../../services/AuthService.js'
import { Pool } from 'pg'
import * as altcha from 'altcha'
import { env } from '../config/env.js'

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

// Username/password registration (requires official ALTCHA token)
authRouter.post('/pwd/register', async (req: express.Request, res: express.Response) => {
  const { username, password, altcha: token } = req.body ?? {}
  if (!username || !password || !token) return res.status(400).json({ error: 'bad_request' })
  try {
    // altcha verification – API shape depends on library version; using a generic verify call
    const result = await altcha.verifyToken(String(token))
    if (!result.valid) {
      return res.status(400).set('X-Auth-Reason', result.reason || 'altcha_failed').json({ error: 'altcha_failed' })
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
