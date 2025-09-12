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
  server.register(multipart, { attachFieldsToBody: true, limits: { files: 3 } });

  // Paths and config
  const SECURE_ROOT = process.env.ADMIN_SECURE_ARTICLES_DIR || '/opt/lwb-admin-api/data/articles';
  const PUBLIC_ROOT = process.env.ADMIN_PUBLIC_ARTICLES_DIR || '/var/www/LWB/Articles';
  const META_FILE = process.env.ADMIN_ARTICLES_META || '/opt/lwb-admin-api/data/articles.json';
  const ADMIN_USER = process.env.ADMIN_PANEL_USERNAME || '';
  const ADMIN_PASS = process.env.ADMIN_PANEL_PASSWORD || '';
  const JWT_SECRET = process.env.ADMIN_PANEL_JWT_SECRET || process.env.PWD_JWT_SECRET || 'CHANGE_ME_DEV';
  ensureDirSync(SECURE_ROOT);
  ensureDirSync(PUBLIC_ROOT);
  ensureDirSync(path.dirname(META_FILE));

  async function readMeta(): Promise<ArticleMeta[]> {
    try { const txt = await fs.readFile(META_FILE, 'utf8'); return JSON.parse(txt) as ArticleMeta[]; } catch { return []; }
  }
  async function writeMeta(items: ArticleMeta[]) { await fs.writeFile(META_FILE, JSON.stringify(items, null, 2), 'utf8'); }
  async function getNextOrder(items: ArticleMeta[]) { return items.length ? Math.max(...items.map(i => i.order)) + 1 : 1; }

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
      if (part && typeof part === 'object' && 'file' in part && part.file) {
        const buffers: Buffer[] = [];
        for await (const chunk of part.file) buffers.push(chunk as Buffer);
        const data = Buffer.concat(buffers);
        const ext = (part.filename as string | undefined)?.split('.').pop() || 'bin';
        const fname = field + '.' + ext.toLowerCase();
        const dstDir = art.publicPath;
        ensureDirSync(dstDir);
        await fs.writeFile(path.join(dstDir, fname), data);
        art[field] = fname as any;
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
    const fileDoc = body.docx || body.file || body.document;
    if (!fileDoc || !fileDoc.file) return reply.code(400).send({ error: 'missing_docx' });
    const docxBufs: Buffer[] = []; for await (const ch of fileDoc.file) docxBufs.push(ch as Buffer);
    const docx = Buffer.concat(docxBufs);
    // Parse docx -> HTML
    const os = await import('os');
    const tmpDocx = path.join(os.tmpdir(), `lwb-docx-${Date.now()}-${Math.random().toString(16).slice(2)}.docx`);
    await fs.writeFile(tmpDocx, docx);
    const result = await MAMMOTH.convertToHtml({ path: tmpDocx });
    try { await fs.unlink(tmpDocx); } catch {}
    const unsafeHtml = result.value || '';
    const html = sanitizeHtml(unsafeHtml, { allowedTags: sanitizeHtml.defaults.allowedTags.concat(['img','iframe','figure','figcaption','a']), allowedAttributes: { '*': ['style'], 'img': ['src','alt'], 'iframe': ['src','allow','allowfullscreen','frameborder'], 'a': ['href','title','target','rel'] } });
    // Determine title/slug
    const baseTitle = titleInput || (fileDoc.filename ? fileDoc.filename.replace(/\.[^.]+$/, '') : 'Untitled');
    const slug = slugify(baseTitle);
    const createdAt = new Date().toISOString();
    const secureDir = path.join(SECURE_ROOT, slug);
    const publicDir = path.join(PUBLIC_ROOT, slug);
    ensureDirSync(secureDir);
    ensureDirSync(publicDir);
    // Store original docx securely
    const originalName = fileDoc.filename || `${slug}.docx`;
    const storedName = `${Date.now()}-${originalName}`;
    await fs.writeFile(path.join(secureDir, storedName), docx);
    // Save cover/icon if provided
    let coverName: string | undefined;
    let iconName: string | undefined;
    for (const [field, out] of [['cover','cover'],['icon','icon']] as const) {
      const p = body[field];
      if (p && p.file) {
        const bufs: Buffer[] = []; for await (const ch of p.file) bufs.push(ch as Buffer);
        const data = Buffer.concat(bufs);
        const ext = (p.filename as string | undefined)?.split('.').pop() || 'bin';
        const fname = `${out}.${ext.toLowerCase()}`;
        await fs.writeFile(path.join(publicDir, fname), data);
        if (out === 'cover') coverName = fname; else iconName = fname;
      }
    }
    // Write simple static bundle: index.html, style.css, script.js
    const styleCss = `/* minimal styles */\nbody{font-family:system-ui,Segoe UI,Roboto,Arial,sans-serif;max-width:820px;margin:2rem auto;padding:0 1rem;line-height:1.6;}h1,h2,h3{line-height:1.25}`;
    const scriptJs = `// placeholder for article interactions`;
    const indexHtml = `<!doctype html>\n<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${baseTitle}</title>${coverName ? `<link rel="preload" as="image" href="./${coverName}">` : ''}<link rel="stylesheet" href="./style.css"></head><body>${html}<script src="./script.js" defer></script></body></html>`;
    await fs.writeFile(path.join(publicDir, 'style.css'), styleCss, 'utf8');
    await fs.writeFile(path.join(publicDir, 'script.js'), scriptJs, 'utf8');
    await fs.writeFile(path.join(publicDir, 'index.html'), indexHtml, 'utf8');

    // Update metadata JSON (append at end)
    const items = await readMeta();
    const order = await getNextOrder(items);
    const item: ArticleMeta = { id: slug, title: baseTitle, createdAt, updatedAt: createdAt, order, filename: originalName, securePath: secureDir, publicPath: publicDir, cover: coverName, icon: iconName };
    const existingIdx = items.findIndex(i => i.id === slug);
    if (existingIdx >= 0) items.splice(existingIdx, 1); // replace if slug existed
    items.push(item);
    await writeMeta(items);
    return reply.code(200).send({ ok: true, item });
  });

  // Users: placeholders — will integrate with Postgres once confirmed schema
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
