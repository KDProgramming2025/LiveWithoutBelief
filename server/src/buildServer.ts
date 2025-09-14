import Fastify, { FastifyInstance, FastifyRequest } from 'fastify';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import { createChallenge, verifySolution } from 'altcha-lib';
import { buildContentStore, computeChecksumForNormalized } from './contentStore.js';
import type { SectionKind } from './contentStore.js';
import { registerAuthRoutes } from './auth/controller.js';
import { AuthService } from './auth/service.js';
import { InMemoryUserRepository, PgUserRepository } from './auth/repository.js';
import crypto from 'crypto';
import { z } from 'zod';
import bcrypt from 'bcryptjs';

export interface ArticleManifestItem {
  id: string; title: string; slug: string; version: number; updatedAt: string; wordCount: number;
}

export interface BuildServerOptions {
  googleClientId: string;
  googleBypass?: boolean; // DEV ONLY: allow bypassing online verification if Google is blocked
  allowedAudiences?: string[]; // optional: additional accepted audiences for bypass mode (e.g., Android client ID)
  altchaBypass?: boolean; // allow skipping ALTCHA verification (tests/dev only)
}


export function buildServer(opts: BuildServerOptions): FastifyInstance {
  const app = Fastify({ logger: true });
  app.register(cors, { origin: true });
  app.register(multipart, { attachFieldsToBody: true, limits: { fileSize: 25 * 1024 * 1024, files: 1 } });

  // Disable caching for all API responses
  app.addHook('onSend', async (_req, reply, payload) => {
    reply.header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
    reply.header('Pragma', 'no-cache');
    reply.header('Expires', '0');
    reply.header('X-Response-Ts', new Date().toISOString());
    return payload as any;
  });

  // Override Fastify default JSON parser to capture raw body & diagnose production failures.
  try { app.removeContentTypeParser('application/json'); } catch (_) { /* ignore */ }
  // Accept application/json with optional charset (e.g., application/json; charset=utf-8)
  app.addContentTypeParser(/^application\/json($|;)/, { parseAs: 'string' }, (req, body, done: (err: Error | null, body: unknown) => void) => {
    const text = body as string;
    (req as FastifyRequest & { rawBody?: string }).rawBody = text;
    try {
      const parsed = text && text.trim().length ? JSON.parse(text) : {};
      done(null, parsed);
    } catch (err: unknown) {
      app.log.error({ err, event: 'json_parse_error', rawLength: text?.length, bodySnippet: text.slice(0, 200) }, 'failed to parse JSON body');
      const e = err instanceof Error ? err : new Error('invalid_json');
      done(e, undefined);
    }
  });

  // Hook to log incoming request basic info (exclude health for noise reduction)
  app.addHook('onRequest', async (req, _reply) => {
    if (req.url === '/health') return;
    app.log.info({ event: 'incoming_request', method: req.method, url: req.url, contentType: req.headers['content-type'], contentLength: req.headers['content-length'] }, 'incoming request');
  });

  // (debug echo endpoint removed in production cleanup)

  // New SOLID wiring: production requires DATABASE_URL; tests use in-memory
  const dbUrl = process.env.DATABASE_URL;
  const users = process.env.NODE_ENV === 'test'
    ? new InMemoryUserRepository()
    : (dbUrl ? new PgUserRepository(dbUrl) : null);
  const content = buildContentStore();
  if (!users) {
    app.log.error({ event: 'startup_failed', reason: 'missing_database_url' }, 'DATABASE_URL is required in production');
    throw new Error('DATABASE_URL missing');
  }
  app.log.info({ event: 'user_repo', impl: users.constructor.name }, 'User repository initialized');
  // ALTCHA configuration
  const ALTCHA_HMAC_KEY = process.env.ALTCHA_HMAC_KEY || process.env.PWD_JWT_SECRET || 'ALTCHA_DEV_ONLY_CHANGE_ME';
  const ALTCHA_MAX_NUMBER = Number(process.env.ALTCHA_MAX_NUMBER || 100000);
  const ALTCHA_EXPIRE_SECONDS = Number(process.env.ALTCHA_EXPIRE_SECONDS || 120); // 2 minutes default
  const ALTCHA_BYPASS = opts.altchaBypass ?? (process.env.NODE_ENV === 'test' && process.env.ALTCHA_DEV_ALLOW !== '0');

  app.get('/health', async () => ({ status: 'ok' }));

  // Minimal Google sign-in endpoint
  const authService = new AuthService(users, {
    googleClientId: opts.googleClientId,
    googleBypass: opts.googleBypass,
    allowedAudiences: opts.allowedAudiences,
  });
  registerAuthRoutes(app, authService);

  // Password-based auth endpoints with ALTCHA
  app.post('/v1/auth/pwd/register', async (req, reply) => {
    const schema = z.object({
      username: z.string().min(3).max(80),
      password: z.string().min(8).max(128),
      // ALTCHA payload submitted by client; Base64 JSON string
      altcha: z.string().min(20),
    });
    const parse = schema.safeParse((req as FastifyRequest).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    const { username, password, altcha } = parse.data;
    try {
      // Verify ALTCHA payload
      const verified = await verifySolution(altcha, ALTCHA_HMAC_KEY, true);
      if (!verified) {
        // Try to decode the ALTCHA payload for diagnostics (non-fatal if parse fails)
        let decoded: any = undefined;
        try {
          const json = Buffer.from(altcha, 'base64').toString('utf8');
          decoded = JSON.parse(json);
        } catch (_) {
          // Some clients may already send a JSON string; keep silent on parse errors
        }
        // Extract helpful hints without leaking sensitive data
        const exp = Number(decoded?.params?.expires ?? decoded?.expires ?? 0);
        const now = Math.floor(Date.now() / 1000);
        const delta = exp ? (exp - now) : undefined;
        const alg = decoded?.algorithm;
        const prefix = decoded?.challengePrefix;
        app.log.warn({ event: 'altcha_failed', username, alg, prefix, exp, now, delta }, 'ALTCHA verification failed');
        reply.header('X-Auth-Reason', 'altcha_failed');
        if (delta !== undefined && delta < 0) reply.header('X-Altcha-Expired', String(-delta));
        return reply.code(400).send({ error: 'altcha_failed' });
      }
      const hash = await bcrypt.hash(password, 10);
      const user = await authService.registerWithPassword(username, hash);
      return reply.code(200).send({ user });
    } catch (e: any) {
      if (e?.code === 'user_exists') return reply.code(409).send({ error: 'user_exists' });
      app.log.error({ err: e }, 'pwd_register_failed');
      return reply.code(500).send({ error: 'server_error' });
    }
  });

  app.post('/v1/auth/pwd/login', async (req, reply) => {
    const schema = z.object({ username: z.string().min(3).max(80), password: z.string().min(1) });
    const parse = schema.safeParse((req as FastifyRequest).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    try {
      const ok = await authService.verifyPassword(parse.data.username, parse.data.password);
      if (!ok) return reply.code(401).send({ error: 'invalid_credentials' });
      const user = await authService.ensureUser(parse.data.username);
      return reply.code(200).send({ user });
    } catch (e) {
      app.log.error({ err: e }, 'pwd_login_failed');
      return reply.code(500).send({ error: 'server_error' });
    }
  });

  // ALTCHA challenge endpoint for clients (Android WebView or in-app fetch)
  app.get('/v1/altcha/challenge', async (req, reply) => {
    const nowSec = Math.floor(Date.now() / 1000);
    const expires = nowSec + ALTCHA_EXPIRE_SECONDS;
  const challenge = await createChallenge({ hmacKey: ALTCHA_HMAC_KEY, maxNumber: ALTCHA_MAX_NUMBER, params: { expires: String(expires) } });
    // Return challenge to client
    return reply.code(200).send(challenge);
  });

  // LWB-48: Article manifests list and fetch
  app.get('/v1/articles/manifest', async (_req, reply) => {
    const items = await content.listManifests();
    return reply.code(200).send({ items });
  });

  app.get('/v1/articles/:id', async (req, reply) => {
    const id = (req.params as { id: string }).id;
    const art = await content.getArticle(id);
    if (!art) return reply.code(404).send({ error: 'not_found' });
    return reply.code(200).send(art);
  });

  // Password-based auth endpoints removed for simplicity per new design.

  // ---- Ingestion: .docx upload ----
  app.post('/v1/ingest/docx', async (req, reply) => {
    try {
      // Body may contain fields and file(s) when attachFieldsToBody=true
      // We expect a single file field named 'file'
  const body = (req as FastifyRequest & { body?: unknown }).body as Record<string, unknown> | undefined;
  let filePart = body && typeof body === 'object' ? (body as Record<string, unknown>)['file'] as { file?: AsyncIterable<unknown> } | undefined : undefined;
      // If not attached, parse via parts iterator as fallback
  if (!filePart || typeof filePart !== 'object' || !('file' in filePart) || !filePart.file) {
        const parts = req.parts();
        for await (const p of parts) {
          if (p.type === 'file' && p.fieldname === 'file') { filePart = p; break; }
        }
      }
      if (!filePart || !filePart.file) return reply.code(400).send({ error: 'missing_file' });
      // Save to a temp file to pass a path to parser (mammoth prefers path)
      const buffers: Buffer[] = [];
      for await (const chunk of filePart.file) { buffers.push(chunk as Buffer); }
      const buf = Buffer.concat(buffers);
      const fs = await import('fs/promises');
      const os = await import('os');
      const path = await import('path');
      const tmp = path.join(os.tmpdir(), `lwb-upload-${Date.now()}-${Math.random().toString(16).slice(2)}.docx`);
      await fs.writeFile(tmp, buf);
      const { parseDocx } = await import('./ingestion/docx.js');
      const parsed = await parseDocx(tmp, { withHtml: true });
      try { await fs.unlink(tmp); } catch { /* ignore */ }
      // Derive title from first heading or filename; id/slug from title
      const firstHeading = parsed.sections.find(s => s.kind === 'heading' && s.text?.trim());
      const filename = (filePart as { filename?: string })?.filename;
      const baseTitle = firstHeading?.text?.trim() || (filename ? filename.replace(/\.[^.]+$/, '') : 'Untitled');
      const slug = baseTitle.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '').slice(0, 80) || 'untitled';
      const title = baseTitle.slice(0, 140);
      const version = 1; // MVP; later from request or store
      // Build normalized checksum (manifest-like)
      const checksum = computeChecksumForNormalized({ id: slug, version, sections: parsed.sections, media: parsed.media });
  const secret = process.env.MANIFEST_SECRET;
  const sig = secret ? crypto.createHmac('sha256', secret).update(checksum).digest('hex') : undefined;
      // Persist
      await content.upsertArticle({
        id: slug,
        slug,
        title,
        version,
        wordCount: parsed.wordCount,
        sections: parsed.sections.map((s, idx) => ({ order: idx, kind: s.kind as SectionKind, level: s.level, text: s.text, html: s.html, mediaRefId: s.mediaRefId })),
        media: parsed.media.map(m => ({ id: m.id, type: m.type, filename: m.filename, contentType: m.contentType, src: m.src, checksum: m.checksum })),
        checksum,
        signature: sig,
        html: parsed.html,
        text: parsed.text,
      });
      const base = { wordCount: parsed.wordCount, sections: parsed.sections.length, media: parsed.media.length };
      // Return manifest if secret exists (as before)
      if (secret) {
        const { buildManifest } = await import('./ingestion/manifest.js');
        const manifest = buildManifest({ id: slug, title, version, sections: parsed.sections, media: parsed.media, wordCount: parsed.wordCount }, secret);
        return reply.code(200).send({ ...base, manifest });
      }
      return reply.code(200).send(base);
    } catch (err) {
      req.log?.error({ err }, 'ingest failed');
      return reply.code(500).send({ error: 'ingest_failed' });
    }
  });

  return app;
}
