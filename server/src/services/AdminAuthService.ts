import argon2 from 'argon2'
import jwt from 'jsonwebtoken'
import { env } from '../server/config/env.js'

export interface AdminSession {
  sub: string
  iat: number
  exp: number
}

export class AdminAuthService {
  constructor(private now: () => number = () => Math.floor(Date.now() / 1000)) {}

  async verifyPassword(username: string, password: string): Promise<boolean> {
    if (username !== env.ADMIN_USER) return false
    if (env.ADMIN_PASSWORD_HASH) {
      return argon2.verify(env.ADMIN_PASSWORD_HASH, password)
    }
    if (env.ADMIN_PASSWORD) {
      return env.ADMIN_PASSWORD === password
    }
    return false
  }

  issueToken(username: string): string {
    const iat = this.now()
    const exp = iat + 60 * 60 * 8 // 8h
    return jwt.sign({ sub: username, iat, exp }, env.ADMIN_JWT_SECRET)
  }

  verifyToken(token: string): AdminSession | null {
    try {
      const decoded = jwt.verify(token, env.ADMIN_JWT_SECRET) as AdminSession
      if (decoded.sub !== env.ADMIN_USER) return null
      return decoded
    } catch {
      return null
    }
  }
}
