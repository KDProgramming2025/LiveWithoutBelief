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

// Simple in-memory implementation useful for tests
export class InMemoryUserRepository implements UserRepository {
  private users = new Map<string, ServerUser & { email?: string }>()
  private creds = new Map<string, string>() // username -> hash
  private seq = 1

  async upsertByEmail(email: string): Promise<{ user: ServerUser; created: boolean }> {
    let existing = [...this.users.values()].find(u => (u as any).email === email)
    if (existing) {
      // Ensure username equals the full email going forward
      existing.username = email
      existing.lastLogin = new Date().toISOString()
      return { user: { id: existing.id, username: existing.username ?? null, createdAt: existing.createdAt, lastLogin: existing.lastLogin }, created: false }
    }
    const id = String(this.seq++)
    const username = email
    const now = new Date().toISOString()
    const user: ServerUser & { email?: string } = { id, username, createdAt: now, lastLogin: now, email }
    this.users.set(id, user)
    return { user: { id, username, createdAt: now, lastLogin: now }, created: true }
  }

  async createWithPassword(username: string, passwordHash: string): Promise<ServerUser | null> {
    const exists = [...this.users.values()].find(u => u.username === username)
    if (exists) return null
    const id = String(this.seq++)
    const now = new Date().toISOString()
    const user: ServerUser = { id, username, createdAt: now, lastLogin: now }
    this.users.set(id, user)
    this.creds.set(username!, passwordHash)
    return user
  }

  async findCredentialsByUsername(username: string): Promise<UserCredentials | null> {
    const user = [...this.users.values()].find(u => u.username === username)
    if (!user) return null
    const hash = this.creds.get(username!)
    if (!hash) return null
    return { user, passwordHash: hash }
  }

  async updateLastLogin(userId: string): Promise<void> {
    const u = this.users.get(userId)
    if (u) u.lastLogin = new Date().toISOString()
  }
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
        // If username differs, update to full email to meet new requirement
        if (row.username !== email) {
          await client.query('UPDATE users SET username = $1 WHERE id = $2', [email, row.id])
          row.username = email
        }
        await client.query('UPDATE users SET last_login = NOW() WHERE id = $1', [row.id])
        await client.query('COMMIT')
        return { user: mapUser(row), created: false }
      }
      const username = email
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
