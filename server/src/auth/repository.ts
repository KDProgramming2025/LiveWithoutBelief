import crypto from 'crypto';
import { Pool, PoolClient } from 'pg';
import type { ConnectionOptions as TlsConnectionOptions } from 'tls';
import type { User } from './domain.js';

export interface UserRepository {
  findByUsername(username: string): Promise<User | undefined>;
  upsertByUsername(username: string): Promise<{ user: User; created: boolean }>;
  touchLastLogin(userId: string): Promise<void>;
}

export class InMemoryUserRepository implements UserRepository {
  private byUsername = new Map<string, User>();
  async findByUsername(username: string): Promise<User | undefined> {
    return this.byUsername.get(username.toLowerCase());
  }
  async upsertByUsername(username: string): Promise<{ user: User; created: boolean }> {
    const key = username.toLowerCase();
    const existing = this.byUsername.get(key);
    if (existing) return { user: existing, created: false };
    const user: User = { id: crypto.randomBytes(12).toString('hex'), username: key, createdAt: new Date().toISOString() };
    this.byUsername.set(key, user);
    return { user, created: true };
  }
  async touchLastLogin(userId: string): Promise<void> {
    for (const u of this.byUsername.values()) if (u.id === userId) { (u as any).lastLogin = new Date().toISOString(); break; }
  }
}

export class PgUserRepository implements UserRepository {
  private pool: Pool;
  private initPromise: Promise<void> | null = null;

  constructor(url: string) {
    this.pool = new Pool({ connectionString: url, ssl: this.sslConfig(url) });
  }

  private sslConfig(url: string): boolean | TlsConnectionOptions {
    if (/localhost|127.0.0.1/.test(url)) return false;
    if (process.env.DB_SSL_DISABLE === '1') return false;
    return { rejectUnauthorized: false } satisfies TlsConnectionOptions;
  }

  private async ensureSchema() {
    if (!this.initPromise) {
      this.initPromise = (async () => {
        const c = await this.pool.connect();
        try {
          await c.query(`CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT NOT NULL UNIQUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            last_login TIMESTAMPTZ
          );`);
        } finally {
          c.release();
        }
      })();
    }
    return this.initPromise;
  }

  private async withClient<T>(fn: (c: PoolClient) => Promise<T>): Promise<T> {
    const c = await this.pool.connect();
    try { return await fn(c); } finally { c.release(); }
  }

  async findByUsername(username: string): Promise<User | undefined> {
    await this.ensureSchema();
    const uname = username.toLowerCase();
    const res = await this.withClient(c => c.query('SELECT id, username, created_at, last_login FROM users WHERE username=$1', [uname]));
    const r = res.rows[0];
    if (!r) return undefined;
    return { id: r.id, username: r.username, createdAt: r.created_at.toISOString?.() || r.created_at, lastLogin: r.last_login?.toISOString?.() || r.last_login };
  }

  async upsertByUsername(username: string): Promise<{ user: User; created: boolean }> {
    await this.ensureSchema();
    const uname = username.toLowerCase();
    // Use a CTE to determine if the row was inserted (created=true) or already existed (created=false)
    const id = crypto.randomBytes(12).toString('hex');
    const res = await this.withClient(c => c.query(
      `WITH ins AS (
         INSERT INTO users (id, username)
         VALUES ($1, $2)
         ON CONFLICT (username) DO NOTHING
         RETURNING id, username, created_at, last_login
       )
       SELECT id, username, created_at, last_login, TRUE AS created FROM ins
       UNION ALL
       SELECT u.id, u.username, u.created_at, u.last_login, FALSE AS created
       FROM users u
       WHERE u.username = $2 AND NOT EXISTS (SELECT 1 FROM ins)`,
      [id, uname]
    ));
    const r = res.rows[0];
    const user: User = {
      id: r.id,
      username: r.username,
      createdAt: r.created_at.toISOString?.() || r.created_at,
      lastLogin: r.last_login?.toISOString?.() || r.last_login,
    };
    const created: boolean = !!r.created;
    return { user, created };
  }

  async touchLastLogin(userId: string): Promise<void> {
    await this.ensureSchema();
    await this.withClient(c => c.query('UPDATE users SET last_login = now() WHERE id = $1', [userId]));
  }
}
