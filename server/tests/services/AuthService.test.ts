import { describe, it, expect } from 'vitest'
import { AuthService } from '../../src/services/AuthService'
import { InMemoryUserRepository } from '../../src/repositories/UserRepository'

describe('AuthService', () => {
  it('registers by email (upsert semantics)', async () => {
    const svc = new AuthService(new InMemoryUserRepository())
    const a = await svc.registerByEmail('a@example.com')
    const b = await svc.registerByEmail('a@example.com')
    expect(a.created).toBe(true)
    expect(b.created).toBe(false)
    expect(a.user.id).toBeTypeOf('string')
    expect(b.user.id).toEqual(a.user.id)
  })

  it('password register/login flow', async () => {
    const svc = new AuthService(new InMemoryUserRepository())
    const user = await svc.passwordRegister('alice', 'secret')
    expect(user).toBeTruthy()
    const dup = await svc.passwordRegister('alice', 'secret')
    expect(dup).toBeNull()
    const ok = await svc.passwordLogin('alice', 'secret')
    expect(ok?.id).toEqual(user?.id)
    const bad = await svc.passwordLogin('alice', 'nope')
    expect(bad).toBeNull()
  })
})
