import Fastify, { FastifyInstance, FastifyRequest } from 'fastify';
import cors from '@fastify/cors';
import cookie from '@fastify/cookie';
import multipart from '@fastify/multipart';
import jwt from 'jsonwebtoken';
import path from 'path';
import fs from 'fs/promises';
import fssync from 'fs';
import crypto from 'crypto';
import sanitizeHtml from 'sanitize-html';
import mammothDefault from 'mammoth';
import os from 'os';

type ArticleMeta = {
  id: string; // slug
  title: string;
  createdAt: string;
  updatedAt: string;
  order: number;
  filename: string; // stored original docx filename
  securePath: string; // /opt/lwb-admin-api/data/articles/slug/...
  publicPath: string; // /var/www/LWB/Articles/slug
  cover?: string; // relative file name under publicPath
  icon?: string;  // relative file name under publicPath
};

type AdminJwt = { sub: string; typ: 'admin'; iat: number; exp: number };

const MAMMOTH = mammothDefault as unknown as {
  images: { inline: (handler: any) => any };
  convertToHtml: (input: { path: string }, options?: { convertImage?: any }) => Promise<{ value: string }>;
};

function ensureDirSync(p: string) { if (!fssync.existsSync(p)) { fssync.mkdirSync(p, { recursive: true }); } }
function bufSha256(b: Buffer) { return crypto.createHash('sha256').update(b).digest('hex'); }
function slugify(s: string) { return s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '').slice(0, 80) || 'untitled'; }

export function buildServer(): FastifyInstance {
  const server = Fastify({ logger: true });
  server.register(cors, { origin: true, credentials: true });
  server.register(cookie, { secret: process.env.ADMIN_PANEL_COOKIE_SECRET || process.env.PWD_JWT_SECRET || 'CHANGE_ME_DEV' });
  // Allow large uploads (DOCX + optional cover/icon) and more parts
  server.register(multipart, {
    attachFieldsToBody: true,
    limits: {
      files: 3,
      parts: 20,
      fields: 50,
      // 256 MB per file to align with nginx client_max_body_size
      fileSize: 256 * 1024 * 1024,
    },
  });

  // Disable caching for all API responses
  server.addHook('onSend', async (_req, reply, payload) => {
    reply.header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
    reply.header('Pragma', 'no-cache');
    reply.header('Expires', '0');
    reply.header('X-Response-Ts', new Date().toISOString());
    return payload as any;
  });

  // Paths and config
  const SECURE_ROOT = process.env.ADMIN_SECURE_ARTICLES_DIR || '/opt/lwb-admin-api/data/articles';
  const PUBLIC_ROOT = process.env.ADMIN_PUBLIC_ARTICLES_DIR || '/var/www/LWB/Articles';
  const META_FILE = process.env.ADMIN_ARTICLES_META || '/opt/lwb-admin-api/data/articles.json';
  const ADMIN_USER = process.env.ADMIN_PANEL_USERNAME || '';
  const ADMIN_PASS = process.env.ADMIN_PANEL_PASSWORD || '';
  const JWT_SECRET = process.env.ADMIN_PANEL_JWT_SECRET || process.env.PWD_JWT_SECRET || 'CHANGE_ME_DEV';
  // Best-effort ensure; don't crash if lacking permissions at boot
  try { ensureDirSync(SECURE_ROOT); } catch (e) { server.log.warn({ err: e }, `Cannot ensure SECURE_ROOT at boot: ${SECURE_ROOT}`); }
  try { ensureDirSync(PUBLIC_ROOT); } catch (e) { server.log.warn({ err: e }, `Cannot ensure PUBLIC_ROOT at boot: ${PUBLIC_ROOT}`); }
  try { ensureDirSync(path.dirname(META_FILE)); } catch (e) { server.log.warn({ err: e }, `Cannot ensure META dir at boot: ${path.dirname(META_FILE)}`); }

  // Metadata cache to avoid transient empty reads on partial writes
  let metaCache: ArticleMeta[] = [];
  let metaInitialized = false;
  async function scanAndBuildMeta(): Promise<ArticleMeta[]> {
    const items: ArticleMeta[] = [];
    try {
      const dirs = await fs.readdir(PUBLIC_ROOT, { withFileTypes: true });
      for (const d of dirs) {
        if (!d.isDirectory()) continue;
        const slug = d.name;
        const publicDir = path.join(PUBLIC_ROOT, slug);
        const secureDir = path.join(SECURE_ROOT, slug);
        const indexPath = path.join(publicDir, 'index.html');
        let title = slug;
        try {
          const html = await fs.readFile(indexPath, 'utf8');
          const m = html.match(/<title>([^<]*)<\/title>/i);
          if (m) title = m[1];
        } catch {}
        const st = await fs.stat(publicDir).catch(() => null as any);
        const createdAt = st?.mtime?.toISOString?.() || new Date().toISOString();
        const updatedAt = createdAt;
        const cover = fssync.existsSync(path.join(publicDir, 'cover.jpg')) ? 'cover.jpg'
          : fssync.existsSync(path.join(publicDir, 'cover.png')) ? 'cover.png'
          : undefined;
        const icon = fssync.existsSync(path.join(publicDir, 'icon.jpg')) ? 'icon.jpg'
          : fssync.existsSync(path.join(publicDir, 'icon.png')) ? 'icon.png'
          : undefined;
        items.push({ id: slug, title, createdAt, updatedAt, order: items.length + 1, filename: `${slug}.docx`, securePath: secureDir, publicPath: publicDir, cover, icon });
      }
    } catch (e) {
      server.log.warn({ err: e }, 'scanAndBuildMeta failed');
    }
    return items;
  }
  async function readMeta(): Promise<ArticleMeta[]> {
    try {
      const txt = await fs.readFile(META_FILE, 'utf8');
      const parsed = JSON.parse(txt) as ArticleMeta[];
      metaCache = parsed;
      return parsed;
    } catch (e) {
      // On first failure and not initialized, try to scan and initialize metadata from the filesystem
      if (!metaInitialized) {
        const scanned = await scanAndBuildMeta();
        if (scanned.length) {
          try { await writeMeta(scanned); } catch {}
          metaInitialized = true;
          metaCache = scanned;
          return scanned;
        }
        metaInitialized = true;
      }
      // Fallback to last good cache if JSON parse fails or file missing
      return metaCache;
    }
  }
  async function writeMeta(items: ArticleMeta[]) {
    const tmp = META_FILE + '.tmp';
    const data = JSON.stringify(items, null, 2);
    await fs.writeFile(tmp, data, 'utf8');
    // Atomic replace so readers never see partial JSON
    await fs.rename(tmp, META_FILE);
    metaCache = items;
  }
  async function getNextOrder(items: ArticleMeta[]) { return items.length ? Math.max(...items.map(i => i.order)) + 1 : 1; }

  // Robustly read a multipart part into a Buffer, supporting different shapes
  async function readPartBuffer(part: any): Promise<{ buffer: Buffer; filename: string | undefined }> {
    if (!part) return { buffer: Buffer.alloc(0), filename: undefined };
    // Common shape when attachFieldsToBody: true
    if (part.file && typeof part.file === 'object' && typeof (part.file as any)[Symbol.asyncIterator] === 'function') {
      const bufs: Buffer[] = [];
      for await (const ch of part.file as any) bufs.push(Buffer.isBuffer(ch) ? ch as Buffer : Buffer.from(ch));
      return { buffer: Buffer.concat(bufs), filename: part.filename as string | undefined };
    }
    // Some libs expose a toBuffer()
    if (typeof part.toBuffer === 'function') {
      const buf: Buffer = await part.toBuffer();
      return { buffer: buf, filename: part.filename as string | undefined };
    }
    // Some provide a value (Buffer|string)
    if (part.value) {
      const val = part.value as any;
      const buf = Buffer.isBuffer(val) ? val : Buffer.from(String(val));
      return { buffer: buf, filename: part.filename as string | undefined };
    }
    // As a last resort, treat the part itself as an async iterable
    if (typeof (part as any)[Symbol.asyncIterator] === 'function') {
      const bufs: Buffer[] = [];
      for await (const ch of part as any) bufs.push(Buffer.isBuffer(ch) ? ch as Buffer : Buffer.from(ch));
      return { buffer: Buffer.concat(bufs), filename: (part as any).filename };
    }
    return { buffer: Buffer.alloc(0), filename: part.filename as string | undefined };
  }

  function setSession(reply: any, username: string) {
    const token = jwt.sign({ sub: username, typ: 'admin' }, JWT_SECRET, { expiresIn: '7d' });
    reply.setCookie('lwb_admin', token, { httpOnly: true, sameSite: 'lax', secure: true, path: '/', maxAge: 7 * 24 * 3600 });
  }
  function clearSession(reply: any) { reply.clearCookie('lwb_admin', { path: '/' }); }
  function requireAdmin(req: FastifyRequest, reply: any): boolean {
    const token = (req.cookies as any)?.['lwb_admin'];
    if (!token) { reply.code(401).send({ error: 'unauthorized' }); return false; }
    try {
      const decoded = jwt.verify(token, JWT_SECRET) as AdminJwt;
      if (decoded.typ !== 'admin') throw new Error('wrong_type');
      return true;
    } catch {
      reply.code(401).send({ error: 'unauthorized' });
      return false;
    }
  }

  // Auth endpoints
  server.post('/v1/admin/login', async (req, reply) => {
    const body = (req.body ?? {}) as { username?: string; password?: string };
    const { username, password } = body;
    if (!username || !password) return reply.code(400).send({ error: 'invalid_body' });
    if (username !== ADMIN_USER || password !== ADMIN_PASS) return reply.code(401).send({ error: 'invalid_credentials' });
    setSession(reply, username);
    return { ok: true, username };
  });
  server.post('/v1/admin/logout', async (_req, reply) => { clearSession(reply); return { ok: true }; });
  server.get('/v1/admin/session', async (req, reply) => {
    const token = (req.cookies as any)?.['lwb_admin'];
    if (!token) return reply.code(200).send({ authenticated: false });
    try { const d = jwt.verify(token, JWT_SECRET) as AdminJwt; return reply.code(200).send({ authenticated: true, username: d.sub }); } catch { return reply.code(200).send({ authenticated: false }); }
  });

  // Articles list
  server.get('/v1/admin/articles', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const items = await readMeta();
    return reply.code(200).send({ items });
  });

  // Move up/down
  server.post('/v1/admin/articles/:id/move', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const id = (req.params as { id: string }).id;
    const dir = ((req.body ?? {}) as { direction?: 'up'|'down' }).direction;
    if (dir !== 'up' && dir !== 'down') return reply.code(400).send({ error: 'invalid_direction' });
    const items = await readMeta();
    const idx = items.findIndex(i => i.id === id);
    if (idx < 0) return reply.code(404).send({ error: 'not_found' });
    const swapIdx = dir === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= items.length) return reply.code(400).send({ error: 'out_of_bounds' });
    const tmpOrder = items[idx].order;
    items[idx].order = items[swapIdx].order;
    items[swapIdx].order = tmpOrder;
    // Also swap positions to maintain visual order
    const [a] = items.splice(idx, 1);
    items.splice(swapIdx, 0, a);
    await writeMeta(items);
    return { ok: true };
  });

  // Edit title/cover/icon (multipart to support optional file replacements)
  server.post('/v1/admin/articles/:id/edit', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const id = (req.params as { id: string }).id;
    const items = await readMeta();
    const idx = items.findIndex(i => i.id === id);
    if (idx < 0) return reply.code(404).send({ error: 'not_found' });
    const art = items[idx];
    const body = (req as FastifyRequest & { body?: any }).body || {};
    if (typeof body.title === 'string' && body.title.trim()) {
      art.title = body.title.trim().slice(0, 200);
    }
    // Files: cover, icon
    for (const field of ['cover','icon'] as const) {
      const part = body[field];
      if (part && typeof part === 'object') {
        const { buffer, filename } = await readPartBuffer(part);
        if (buffer.length > 0) {
          const ext = (filename || '').split('.').pop() || 'bin';
          const fname = field + '.' + String(ext).toLowerCase();
          const dstDir = art.publicPath;
          ensureDirSync(dstDir);
          await fs.writeFile(path.join(dstDir, fname), buffer);
          art[field] = fname as any;
        }
      }
    }
    art.updatedAt = new Date().toISOString();
    items[idx] = art;
    await writeMeta(items);
    return { ok: true, item: art };
  });

  // Upload DOCX + cover + icon, convert to HTML/CSS/JS under PUBLIC_ROOT/slug
  server.post('/v1/admin/articles/upload', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const body = (req as FastifyRequest & { body?: any }).body || {};
    const titleInput: string | undefined = typeof body.title === 'string' ? body.title.trim() : undefined;
    let upName: string | undefined;
    let docx: Buffer | undefined;
    let coverBuf: Buffer | undefined;
    let coverOrig: string | undefined;
    let iconBuf: Buffer | undefined;
    let iconOrig: string | undefined;

    // First attempt: read from attachFieldsToBody representation
    const fileDoc = body.docx || body.file || body.document;
    if (fileDoc) {
      const r = await readPartBuffer(fileDoc);
      docx = r.buffer; upName = r.filename || upName;
    }
    if (body.cover && typeof body.cover === 'object') {
      const r = await readPartBuffer(body.cover);
      if (r.buffer?.length) { coverBuf = r.buffer; coverOrig = r.filename; }
    }
    if (body.icon && typeof body.icon === 'object') {
      const r = await readPartBuffer(body.icon);
      if (r.buffer?.length) { iconBuf = r.buffer; iconOrig = r.filename; }
    }

    // Fallback: if DOCX missing/empty, use saveRequestFiles() to persist and read
    if (!docx || docx.length < 4) {
      try {
        const saver: any = (req as any).saveRequestFiles ? (req as any) : null;
        if (saver) {
          const saved: Array<any> = await saver.saveRequestFiles();
          for (const f of saved) {
            try {
              const buf = await fs.readFile(f.filepath);
              if ((f.fieldname === 'docx' || f.fieldname === 'file' || f.fieldname === 'document') && (!docx || docx.length < 4)) {
                docx = buf; upName = f.filename || upName;
              } else if (f.fieldname === 'cover' && !coverBuf) {
                coverBuf = buf; coverOrig = f.filename;
              } else if (f.fieldname === 'icon' && !iconBuf) {
                iconBuf = buf; iconOrig = f.filename;
              }
            } finally {
              try { await fs.unlink(f.filepath); } catch (e) { req.log.warn({ err: e, file: f.filepath }, 'cleanup tmp file failed'); }
            }
          }
        }
      } catch (e) {
        req.log.warn({ err: e }, 'saveRequestFiles fallback failed');
      }
    }

    if (!docx || docx.length < 4) {
      req.log.error({ len: docx ? docx.length : 0 }, 'Upload DOCX buffer is empty');
      return reply.code(400).send({ error: 'invalid_docx' });
    }
    // Log diagnostics
    const hexSig = docx.subarray(0, 4).toString('hex');
    req.log.info({ len: docx.length, hexSig, upName }, 'DOCX upload diagnostics');
    // Basic ZIP header validation
    if (!(hexSig === '504b0304' || hexSig === '504b0506' || hexSig === '504b0708')) {
      return reply.code(400).send({ error: 'invalid_docx_zip' });
    }
    // Parse docx -> HTML: write to a temp file and pass { path } for maximum compatibility
    const tmpBase = await fs.mkdtemp(path.join(os.tmpdir(), 'lwb-docx-'));
    const tmpDocx = path.join(tmpBase, upName || 'upload.docx');
    try {
      await fs.writeFile(tmpDocx, docx);
      const result = await MAMMOTH.convertToHtml({ path: tmpDocx } as any);
      const unsafeHtml = result.value || '';
      const html = sanitizeHtml(unsafeHtml, { allowedTags: sanitizeHtml.defaults.allowedTags.concat(['img','iframe','figure','figcaption','a']), allowedAttributes: { '*': ['style'], 'img': ['src','alt'], 'iframe': ['src','allow','allowfullscreen','frameborder'], 'a': ['href','title','target','rel'] } });
      // Determine title/slug
      const baseTitle = titleInput || (upName ? upName.replace(/\.[^.]+$/, '') : 'Untitled');
      const slug = slugify(baseTitle);
      const createdAt = new Date().toISOString();
      const secureDir = path.join(SECURE_ROOT, slug);
      const publicDir = path.join(PUBLIC_ROOT, slug);
      try { ensureDirSync(secureDir); } catch (e) { server.log.error({ err: e, secureDir }, 'Cannot ensure secureDir'); return reply.code(500).send({ error: 'server_storage_unavailable' }); }
      try { ensureDirSync(publicDir); } catch (e) { server.log.error({ err: e, publicDir }, 'Cannot ensure publicDir'); return reply.code(500).send({ error: 'server_storage_unavailable' }); }
      // Store original docx securely
      const originalName = upName || `${slug}.docx`;
      const storedName = `${Date.now()}-${originalName}`;
      await fs.writeFile(path.join(secureDir, storedName), docx);
      // Save cover/icon if provided
      let coverName: string | undefined;
      let iconName: string | undefined;
      if (coverBuf && coverBuf.length) {
        const ext = (coverOrig || '').split('.').pop() || 'bin';
        coverName = `cover.${String(ext).toLowerCase()}`;
        await fs.writeFile(path.join(publicDir, coverName), coverBuf);
      }
      if (iconBuf && iconBuf.length) {
        const ext = (iconOrig || '').split('.').pop() || 'bin';
        iconName = `icon.${String(ext).toLowerCase()}`;
        await fs.writeFile(path.join(publicDir, iconName), iconBuf);
      }
      // Write simple static bundle: index.html, style.css, script.js
      const styleCss = `/* minimal styles */\nbody{font-family:system-ui,Segoe UI,Roboto,Arial,sans-serif;max-width:820px;margin:2rem auto;padding:0 1rem;line-height:1.6;}h1,h2,h3{line-height:1.25}`;
      const scriptJs = `// placeholder for article interactions`;
      const indexHtml = `<!doctype html>\n<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${baseTitle}</title>${coverName ? `<link rel=\"preload\" as=\"image\" href=\"./${coverName}\">` : ''}<link rel="stylesheet" href="./style.css"></head><body>${html}<script src="./script.js" defer></script></body></html>`;
      await fs.writeFile(path.join(publicDir, 'style.css'), styleCss, 'utf8');
      await fs.writeFile(path.join(publicDir, 'script.js'), scriptJs, 'utf8');
      await fs.writeFile(path.join(publicDir, 'index.html'), indexHtml, 'utf8');
      // Update metadata JSON (append at end)
      const items = await readMeta();
      const order = await getNextOrder(items);
      const item: ArticleMeta = { id: slug, title: baseTitle, createdAt, updatedAt: createdAt, order, filename: originalName, securePath: secureDir, publicPath: publicDir, cover: coverName, icon: iconName };
      const existingIdx = items.findIndex(i => i.id === slug);
      if (existingIdx >= 0) items.splice(existingIdx, 1);
      items.push(item);
      try { await writeMeta(items); } catch (e) { server.log.error({ err: e, meta: META_FILE }, 'Cannot write metadata'); }
      return reply.code(200).send({ ok: true, item });
    } finally {
      // best-effort cleanup
      try { await fs.unlink(tmpDocx); } catch {}
      try { await fs.rmdir(tmpBase); } catch {}
    }
  });

  // Users: placeholders — will integrate with Postgres once confirmed schema
  // Maintenance: Reindex metadata from PUBLIC_ROOT/SECURE_ROOT if articles.json is missing or corrupted
  server.post('/v1/admin/articles/reindex', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const items = await scanAndBuildMeta();
    await writeMeta(items);
    return { ok: true, count: items.length };
  });
  server.get('/v1/admin/users/summary', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    // TODO: replace with DB query (users count)
    return { total: 0 };
  });
  server.get('/v1/admin/users/search', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const q = (req.query as { q?: string }).q || '';
    // TODO: replace with DB query to fetch username, createdAt, bookmarksCount, discussionsCount, lastLogin
    return { query: q, users: [] };
  });
  server.delete('/v1/admin/users/:id', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const id = (req.params as { id: string }).id;
    // TODO: replace with DB deletion or deactivation
    return { ok: false, error: 'not_implemented', id };
  });

  return server;
}
