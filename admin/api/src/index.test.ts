import { describe, it, expect } from 'vitest'
import { buildServer } from './index'

describe('admin api', () => {
  it('returns queue status', async () => {
    const app = buildServer()
    const res = await app.inject({ method: 'GET', url: '/v1/admin/ingestion/queue' })
    expect(res.statusCode).toBe(200)
    const body = res.json() as any
    expect(body).toHaveProperty('pending')
    expect(Array.isArray(body.items)).toBe(true)
  })
})
