import { describe, it, expect } from 'vitest'
import { buildServer } from './index'

describe('admin api', () => {
  it('session shows unauthenticated when no cookie', async () => {
    const app = buildServer()
    const res = await app.inject({ method: 'GET', url: '/v1/admin/session' })
    expect(res.statusCode).toBe(200)
    const body = res.json() as any
    expect(body).toHaveProperty('authenticated')
  })

  it('articles requires auth', async () => {
    const app = buildServer()
    const res = await app.inject({ method: 'GET', url: '/v1/admin/articles' })
    expect(res.statusCode).toBe(401)
  })
})
