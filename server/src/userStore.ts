import crypto from 'crypto';
import { Pool, PoolClient } from 'pg';
import type { ConnectionOptions as TlsConnectionOptions } from 'tls';

export interface UserRecord { id: string; username: string; passwordHash: string; createdAt: string; }
export interface UserSummary { id: string; username: string; createdAt: string; }
export interface UserStore {
  create(username: string, passwordHash: string): Promise<UserRecord | null> | UserRecord | null;
  findByUsername(username: string): Promise<UserRecord | undefined> | UserRecord | undefined;
  count(): Promise<number> | number;
  search(q: string, limit: number, offset: number): Promise<UserSummary[]> | UserSummary[];
}

// In-memory implementation (used for tests / fallback)
export class InMemoryUserStore implements UserStore {
  private byUsername = new Map<string, UserRecord>();
  create(username: string, passwordHash: string): UserRecord | null {
    const key = username.toLowerCase();
    if (this.byUsername.has(key)) return null;
    const rec: UserRecord = { id: crypto.randomBytes(12).toString('hex'), username: key, passwordHash, createdAt: new Date().toISOString() };
    this.byUsername.set(key, rec);
    return rec;
  }
  findByUsername(username: string): UserRecord | undefined { return this.byUsername.get(username.toLowerCase()); }
  count(): number { return this.byUsername.size; }
  search(q: string, limit: number, offset: number): UserSummary[] {
    const needle = q.trim().toLowerCase();
    const all = Array.from(this.byUsername.values())
      .filter(u => !needle || u.username.includes(needle))
      .sort((a,b) => (a.createdAt < b.createdAt ? 1 : -1));
    return all.slice(offset, offset + limit).map(u => ({ id: u.id, username: u.username, createdAt: u.createdAt }));
  }
}

// Postgres implementation
export class PostgresUserStore implements UserStore {
  private pool: Pool;
  private initPromise: Promise<void> | null = null;

  constructor(private url: string) {
    this.pool = new Pool({ connectionString: url, ssl: this.sslConfig(url) });
  }

  private sslConfig(url: string): boolean | TlsConnectionOptions {
    if (/localhost|127.0.0.1/.test(url)) return false;
    // Allow disable via env
    if (process.env.DB_SSL_DISABLE === '1') return false;
    const cfg: TlsConnectionOptions = { rejectUnauthorized: false };
    return cfg; // managed services often need this relaxed unless CA provided
  }

  private async ensureSchema() {
    if (!this.initPromise) {
      this.initPromise = (async () => {
        const client = await this.pool.connect();
        try {
          await client.query(`CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
          );`);
        } finally {
          client.release();
        }
      })();
    }
    return this.initPromise;
  }

  private async withClient<T>(fn: (c: PoolClient) => Promise<T>): Promise<T> {
    const c = await this.pool.connect();
    try { return await fn(c); } finally { c.release(); }
  }

  async create(username: string, passwordHash: string): Promise<UserRecord | null> {
    await this.ensureSchema();
    const id = crypto.randomBytes(12).toString('hex');
    const uname = username.toLowerCase();
    try {
      const res = await this.withClient(c => c.query(
        'INSERT INTO users (id, username, password_hash) VALUES ($1,$2,$3) RETURNING id, username, password_hash, created_at',
        [id, uname, passwordHash]
      ));
      const row = res.rows[0];
      return { id: row.id, username: row.username, passwordHash: row.password_hash, createdAt: row.created_at.toISOString?.() || row.created_at };
    } catch (e: unknown) {
      const code = (e && typeof e === 'object' && 'code' in e) ? (e as { code?: string }).code : undefined;
      if (code === '23505') return null; // unique violation
      throw e;
    }
  }

  async findByUsername(username: string): Promise<UserRecord | undefined> {
    await this.ensureSchema();
    const uname = username.toLowerCase();
    const res = await this.withClient(c => c.query('SELECT id, username, password_hash, created_at FROM users WHERE username=$1', [uname]));
    const row = res.rows[0];
    if (!row) return undefined;
    return { id: row.id, username: row.username, passwordHash: row.password_hash, createdAt: row.created_at.toISOString?.() || row.created_at };
  }

  async count(): Promise<number> {
    await this.ensureSchema();
    const res = await this.withClient(c => c.query('SELECT COUNT(*)::int AS cnt FROM users'));
    return Number(res.rows[0]?.cnt || 0);
  }

  async search(q: string, limit: number, offset: number): Promise<UserSummary[]> {
    await this.ensureSchema();
    const needle = `%${q.trim().toLowerCase()}%`;
    const res = await this.withClient(c => c.query(
      'SELECT id, username, created_at FROM users WHERE $1 = $$%%$$ OR username ILIKE $1 ORDER BY created_at DESC LIMIT $2 OFFSET $3',
      [needle, Math.max(1, Math.min(200, limit | 0)), Math.max(0, offset | 0)]
    ));
    return res.rows.map(r => ({ id: r.id, username: r.username, createdAt: r.created_at.toISOString?.() || r.created_at }));
  }
}

export function buildUserStore(): UserStore {
  const url = process.env.DATABASE_URL;
  if (url && process.env.NODE_ENV !== 'test') {
    return new PostgresUserStore(url);
  }
  return new InMemoryUserStore();
}
