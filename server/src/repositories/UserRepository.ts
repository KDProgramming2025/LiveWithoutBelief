import { ServerUser } from '../domain/user.js'

// Minimal Pool-like interface to avoid depending on @types/pg at build time
interface PgClientLike {
  query: (text: string, params?: any[]) => Promise<any>
  release: () => void
}
interface PgPoolLike {
  connect: () => Promise<PgClientLike>
  query: (text: string, params?: any[]) => Promise<any>
}

export interface UserCredentials {
  user: ServerUser
  passwordHash: string
}

export interface UserRepository {
  upsertByEmail(email: string): Promise<{ user: ServerUser; created: boolean }>
  createWithPassword(username: string, passwordHash: string): Promise<ServerUser | null>
  findCredentialsByUsername(username: string): Promise<UserCredentials | null>
  updateLastLogin(userId: string): Promise<void>
}

export class PgUserRepository implements UserRepository {
  constructor(private readonly pool: PgPoolLike) {}

  async upsertByEmail(email: string): Promise<{ user: ServerUser; created: boolean }> {
    const client = await this.pool.connect()
    try {
      await client.query('BEGIN')
      const sel = await client.query('SELECT id, username, created_at, last_login FROM users WHERE email = $1', [email])
      if (sel.rowCount && sel.rowCount > 0) {
        const row = sel.rows[0]
        await client.query('UPDATE users SET last_login = NOW() WHERE id = $1', [row.id])
        await client.query('COMMIT')
        return { user: mapUser(row), created: false }
      }
      const username = email.split('@')[0]
      const ins = await client.query(
        'INSERT INTO users (email, username, created_at, last_login) VALUES ($1, $2, NOW(), NOW()) RETURNING id, username, created_at, last_login',
        [email, username],
      )
      await client.query('COMMIT')
      return { user: mapUser(ins.rows[0]), created: true }
    } catch (e) {
      await client.query('ROLLBACK')
      throw e
    } finally {
      client.release()
    }
  }

  async createWithPassword(username: string, passwordHash: string): Promise<ServerUser | null> {
    const client = await this.pool.connect()
    try {
      await client.query('BEGIN')
      const exists = await client.query('SELECT 1 FROM users WHERE username = $1', [username])
      if (exists.rowCount && exists.rowCount > 0) {
        await client.query('ROLLBACK')
        return null
      }
      const ins = await client.query(
        'INSERT INTO users (username, created_at, last_login) VALUES ($1, NOW(), NOW()) RETURNING id, username, created_at, last_login',
        [username],
      )
      const userId = ins.rows[0].id
      await client.query(
        'INSERT INTO user_credentials (user_id, password_hash, created_at) VALUES ($1, $2, NOW())',
        [userId, passwordHash],
      )
      await client.query('COMMIT')
      return mapUser(ins.rows[0])
    } catch (e) {
      await client.query('ROLLBACK')
      throw e
    } finally {
      client.release()
    }
  }

  async findCredentialsByUsername(username: string): Promise<UserCredentials | null> {
    const sql = `
      SELECT u.id, u.username, u.created_at, u.last_login, c.password_hash
      FROM users u
      JOIN user_credentials c ON c.user_id = u.id
      WHERE u.username = $1
    `
    const r = await this.pool.query(sql, [username])
    if (!r.rowCount) return null
    const row = r.rows[0]
    return { user: mapUser(row), passwordHash: row.password_hash }
  }

  async updateLastLogin(userId: string): Promise<void> {
    await this.pool.query('UPDATE users SET last_login = NOW() WHERE id = $1', [userId])
  }
}

function mapUser(row: any): ServerUser {
  return {
    id: String(row.id),
    username: row.username ?? null,
    createdAt: row.created_at ? new Date(row.created_at).toISOString() : undefined,
    lastLogin: row.last_login ? new Date(row.last_login).toISOString() : undefined,
  }
}
