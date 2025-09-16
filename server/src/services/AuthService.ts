import argon2 from 'argon2'
import { UserRepository } from '../repositories/UserRepository.js'

export class AuthService {
  constructor(private readonly users: UserRepository) {}

  async registerByEmail(email: string) {
    return this.users.upsertByEmail(email)
  }

  async passwordRegister(username: string, password: string) {
    const hash = await argon2.hash(password, { type: argon2.argon2id })
    return this.users.createWithPassword(username, hash)
  }

  async passwordLogin(username: string, password: string) {
    const rec = await this.users.findCredentialsByUsername(username)
    if (!rec) return null
    const ok = await argon2.verify(rec.passwordHash, password)
    if (!ok) return null
    await this.users.updateLastLogin(rec.user.id)
    return rec.user
  }
}
