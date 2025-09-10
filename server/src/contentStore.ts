import crypto from 'crypto';
import { Pool, PoolClient } from 'pg';

export type SectionKind = 'heading' | 'paragraph' | 'list' | 'quote' | 'image' | 'audio' | 'video' | 'embed';

export interface ArticleRecord {
  id: string;
  slug: string;
  title: string;
  version: number;
  wordCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface SectionRecord {
  order: number;
  kind: SectionKind;
  level?: number | null;
  text?: string | null;
  html?: string | null;
  mediaRefId?: string | null;
}

export interface MediaRecord {
  id: string;
  type: 'image' | 'audio' | 'video' | 'embed';
  filename?: string | null;
  contentType?: string | null;
  src?: string | null;
  checksum?: string | null;
}

export interface StoredArticle extends ArticleRecord {
  sections: SectionRecord[];
  media: MediaRecord[];
  checksum: string; // checksum over normalized content
  signature?: string | null;
  html?: string | null; // optional consolidated sanitized HTML
  text?: string | null; // optional consolidated plain text
}

export interface ContentStore {
  upsertArticle(article: Omit<StoredArticle, 'createdAt'|'updatedAt'>): Promise<void>;
  listManifests(): Promise<Pick<ArticleRecord,'id'|'title'|'slug'|'version'|'updatedAt'|'wordCount'>[]>;
  getArticle(id: string): Promise<StoredArticle | undefined>;
}

// In-memory implementation for tests/dev
export class InMemoryContentStore implements ContentStore {
  private byId = new Map<string, StoredArticle>();
  async upsertArticle(article: Omit<StoredArticle, 'createdAt'|'updatedAt'>): Promise<void> {
    const now = new Date().toISOString();
    const existing = this.byId.get(article.id);
    const record: StoredArticle = { ...article, createdAt: existing?.createdAt ?? now, updatedAt: now };
    this.byId.set(article.id, record);
  }
  async listManifests() { return Array.from(this.byId.values()).map(a => ({ id: a.id, title: a.title, slug: a.slug, version: a.version, updatedAt: a.updatedAt, wordCount: a.wordCount })); }
  async getArticle(id: string) { return this.byId.get(id); }
}

// Postgres implementation
export class PostgresContentStore implements ContentStore {
  private pool: Pool;
  private initPromise: Promise<void> | null = null;
  constructor(private url: string) { this.pool = new Pool({ connectionString: url, ssl: this.sslConfig(url) }); }
  private sslConfig(url: string) {
    if (/localhost|127.0.0.1/.test(url)) return false;
    if (process.env.DB_SSL_DISABLE === '1') return false;
    return { rejectUnauthorized: false };
  }
  private async withClient<T>(fn: (c: PoolClient) => Promise<T>): Promise<T> { const c = await this.pool.connect(); try { return await fn(c); } finally { c.release(); } }
  private async ensureSchema() {
    if (!this.initPromise) {
      this.initPromise = this.withClient(async (c) => {
        await c.query(`CREATE TABLE IF NOT EXISTS articles (
          id TEXT PRIMARY KEY,
          slug TEXT NOT NULL,
          title TEXT NOT NULL,
          version INTEGER NOT NULL,
          word_count INTEGER NOT NULL,
          checksum TEXT NOT NULL,
          signature TEXT,
          html TEXT,
          text TEXT,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );`);
        await c.query(`CREATE TABLE IF NOT EXISTS article_sections (
          article_id TEXT NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
          ord INTEGER NOT NULL,
          kind TEXT NOT NULL,
          level INTEGER,
          text TEXT,
          html TEXT,
          media_ref_id TEXT,
          PRIMARY KEY(article_id, ord)
        );`);
        await c.query(`CREATE TABLE IF NOT EXISTS media_assets (
          article_id TEXT NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
          id TEXT NOT NULL,
          type TEXT NOT NULL,
          filename TEXT,
          content_type TEXT,
          src TEXT,
          checksum TEXT,
          PRIMARY KEY(article_id, id)
        );`);
      });
    }
    return this.initPromise;
  }
  async upsertArticle(article: Omit<StoredArticle, 'createdAt'|'updatedAt'>): Promise<void> {
    await this.ensureSchema();
    await this.withClient(async (c) => {
      await c.query('BEGIN');
      try {
        await c.query(`INSERT INTO articles (id, slug, title, version, word_count, checksum, signature, html, text)
          VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
          ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, title=EXCLUDED.title, version=EXCLUDED.version, word_count=EXCLUDED.word_count, checksum=EXCLUDED.checksum, signature=EXCLUDED.signature, html=EXCLUDED.html, text=EXCLUDED.text, updated_at=now()`,
          [article.id, article.slug, article.title, article.version, article.wordCount, article.checksum, article.signature ?? null, article.html ?? null, article.text ?? null]);
        await c.query('DELETE FROM article_sections WHERE article_id=$1', [article.id]);
        for (const s of article.sections) {
          await c.query('INSERT INTO article_sections (article_id, ord, kind, level, text, html, media_ref_id) VALUES ($1,$2,$3,$4,$5,$6,$7)',
            [article.id, s.order, s.kind, s.level ?? null, s.text ?? null, s.html ?? null, s.mediaRefId ?? null]);
        }
        await c.query('DELETE FROM media_assets WHERE article_id=$1', [article.id]);
        for (const m of article.media) {
          await c.query('INSERT INTO media_assets (article_id, id, type, filename, content_type, src, checksum) VALUES ($1,$2,$3,$4,$5,$6,$7)',
            [article.id, m.id, m.type, m.filename ?? null, m.contentType ?? null, m.src ?? null, m.checksum ?? null]);
        }
        await c.query('COMMIT');
      } catch (e) {
        await c.query('ROLLBACK');
        throw e;
      }
    });
  }
  async listManifests() {
    await this.ensureSchema();
    return this.withClient(async (c) => {
      const res = await c.query('SELECT id, title, slug, version, word_count, updated_at FROM articles ORDER BY updated_at DESC');
      return res.rows.map(r => ({ id: r.id, title: r.title, slug: r.slug, version: Number(r.version), updatedAt: r.updated_at.toISOString?.() || r.updated_at, wordCount: Number(r.word_count) }));
    });
  }
  async getArticle(id: string) {
    await this.ensureSchema();
    return this.withClient(async (c) => {
      const a = await c.query('SELECT id, slug, title, version, word_count, checksum, signature, html, text, created_at, updated_at FROM articles WHERE id=$1', [id]);
      if (!a.rowCount) return undefined;
      const artRow = a.rows[0];
      const secs = await c.query('SELECT ord, kind, level, text, html, media_ref_id FROM article_sections WHERE article_id=$1 ORDER BY ord', [id]);
      const media = await c.query('SELECT id, type, filename, content_type, src, checksum FROM media_assets WHERE article_id=$1', [id]);
      type SectionRow = { ord: number | string; kind: SectionKind; level: number | null; text: string | null; html: string | null; media_ref_id: string | null };
      type MediaRow = { id: string; type: MediaRecord['type']; filename: string | null; content_type: string | null; src: string | null; checksum: string | null };
      return {
        id: artRow.id,
        slug: artRow.slug,
        title: artRow.title,
        version: Number(artRow.version),
        wordCount: Number(artRow.word_count),
        checksum: artRow.checksum,
        signature: artRow.signature,
        html: artRow.html,
        text: artRow.text,
        createdAt: artRow.created_at.toISOString?.() || artRow.created_at,
        updatedAt: artRow.updated_at.toISOString?.() || artRow.updated_at,
        sections: (secs.rows as SectionRow[]).map((r) => ({ order: Number(r.ord), kind: r.kind, level: r.level ?? undefined, text: r.text ?? undefined, html: r.html ?? undefined, mediaRefId: r.media_ref_id ?? undefined })),
        media: (media.rows as MediaRow[]).map((r) => ({ id: r.id, type: r.type, filename: r.filename ?? undefined, contentType: r.content_type ?? undefined, src: r.src ?? undefined, checksum: r.checksum ?? undefined }))
      } as StoredArticle;
    });
  }
}

export function buildContentStore(): ContentStore {
  const url = process.env.DATABASE_URL;
  if (url && process.env.NODE_ENV !== 'test') return new PostgresContentStore(url);
  return new InMemoryContentStore();
}

export function computeChecksumForNormalized(input: { sections: { kind: string; level?: number; text?: string; mediaRefId?: string | null }[]; media: { type: string; filename?: string | null; checksum?: string | null; src?: string | null }[]; version: number; id: string; }): string {
  const normalized = JSON.stringify({
    id: input.id,
    version: input.version,
    sections: input.sections.map(s => ({ k: s.kind, l: s.level, t: s.text, m: s.mediaRefId ?? null })),
    media: input.media.map(m => ({ t: m.type, f: m.filename ?? null, c: m.checksum ?? null, s: m.src ?? null })),
  });
  return crypto.createHash('sha256').update(normalized).digest('hex');
}
