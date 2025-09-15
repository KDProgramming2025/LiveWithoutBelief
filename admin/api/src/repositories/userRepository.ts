import type { UserSummary } from '../types'
import { CONFIG } from '../config'

export interface UserRepository {
  count(): Promise<number>
  search(q: string, limit: number, offset: number): Promise<UserSummary[]>
  softDelete?(id: string): Promise<{ deleted: boolean; notFound?: boolean }>
}

export function buildUserRepository(): UserRepository {
  const url = CONFIG.DB_URL
  if (!url) {
    return {
      async count() { return 0 },
      async search(_q: string, _limit: number, _offset: number) { return [] },
    }
  }
  const { Pool } = require('pg') as typeof import('pg')
  const pool = new Pool({ connectionString: url, ssl: /localhost|127.0.0.1/.test(url) ? false : { rejectUnauthorized: false } })

  let initPromise: Promise<void> | null = null
  async function ensureSchema() {
    if (!initPromise) {
      initPromise = (async () => {
        const client = await pool.connect()
        try {
          await client.query(`CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
          );`)
          await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login TIMESTAMPTZ')
          await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ')
          try { await client.query('DELETE FROM users WHERE deleted_at IS NOT NULL') } catch {}
        } finally { client.release() }
      })()
    }
    return initPromise
  }

  async function query<T = any>(text: string, params?: any[]): Promise<{ rows: T[]; rowCount: number }> {
    const client = await pool.connect()
    try {
      const res = await client.query(text, params)
      return { rows: res.rows as T[], rowCount: (res as any)?.rowCount ?? 0 }
    } finally { client.release() }
  }

  return {
    async count(): Promise<number> {
      await ensureSchema()
      const r = await query<{ cnt: number }>('SELECT COUNT(*)::int AS cnt FROM users')
      return Number((r.rows[0] as any)?.cnt || 0)
    },
    async search(q: string, limit: number, offset: number): Promise<UserSummary[]> {
      await ensureSchema()
      const needle = q.trim() ? `%${q.trim().toLowerCase()}%` : null
      const sql = needle ? 'SELECT id, username, created_at, last_login FROM users WHERE username ILIKE $1 ORDER BY created_at DESC LIMIT $2 OFFSET $3'
        : 'SELECT id, username, created_at, last_login FROM users ORDER BY created_at DESC LIMIT $1 OFFSET $2'
      const args = needle ? [needle, Math.max(1, Math.min(200, limit | 0)), Math.max(0, offset | 0)]
        : [Math.max(1, Math.min(200, limit | 0)), Math.max(0, offset | 0)]
      const r = await query(sql, args as any)
      return r.rows.map((row: any) => ({ id: row.id, username: row.username, createdAt: row.created_at?.toISOString?.() || row.created_at, lastLogin: row.last_login?.toISOString?.() || row.last_login }))
    },
    async softDelete(id: string): Promise<{ deleted: boolean; notFound?: boolean }> {
      await ensureSchema()
      const r = await query('DELETE FROM users WHERE id = $1', [id])
      if (r.rowCount > 0) return { deleted: true }
      return { deleted: false, notFound: true }
    },
  }
}
