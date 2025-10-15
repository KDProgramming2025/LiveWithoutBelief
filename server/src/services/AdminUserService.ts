type PgPool = { query: (text: string, params?: any[]) => Promise<any> }

export interface AdminUserListItem {
  id: string
  username: string | null
  registeredAt: string
  threads: number
  lastLogin: string | null
}

export class AdminUserService {
  constructor(private readonly pool: PgPool) {}

  async countUsers(): Promise<number> {
    const r = await this.pool.query('SELECT COUNT(*)::int AS c FROM users')
    return r.rows[0]?.c ?? 0
  }

  async listUsers(q: string | undefined, limit = 50, offset = 0): Promise<AdminUserListItem[]> {
    const params: any[] = []
    let where = ''
    if (q && q.trim().length > 0) {
      params.push(`%${q.trim()}%`)
      where = 'WHERE (username ILIKE $1 OR email ILIKE $1)'
    }
    const sql = `
      SELECT u.id, u.username, u.created_at, u.last_login,
             0 AS threads
      FROM users u
      ${where}
      ORDER BY u.created_at DESC
      LIMIT ${limit} OFFSET ${offset}
    `
    const r = await this.pool.query(sql, params)
    return r.rows.map((row: any) => ({
      id: String(row.id),
      username: row.username ?? null,
      registeredAt: row.created_at ? new Date(row.created_at).toISOString() : '',
      threads: Number(row.threads ?? 0),
      lastLogin: row.last_login ? new Date(row.last_login).toISOString() : null,
    }))
  }

  async deleteUser(userId: string): Promise<boolean> {
    const r = await this.pool.query('DELETE FROM users WHERE id = $1', [userId])
    return r.rowCount > 0
  }
}
