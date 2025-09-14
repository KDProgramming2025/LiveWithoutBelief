import Fastify, { FastifyInstance, FastifyRequest } from 'fastify';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import { OAuth2Client, TokenPayload } from 'google-auth-library';
import { z } from 'zod';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { buildUserStore } from './userStore.js';
import { createChallenge, verifySolution } from 'altcha-lib';
import { buildContentStore, computeChecksumForNormalized } from './contentStore.js';
import type { SectionKind } from './contentStore.js';
import crypto from 'crypto';

export interface ArticleManifestItem {
  id: string; title: string; slug: string; version: number; updatedAt: string; wordCount: number;
}

export interface RevocationStore {
  isRevoked(token: string): Promise<boolean> | boolean;
  revoke(token: string): Promise<void> | void;
}

export class InMemoryRevocationStore implements RevocationStore {
  private set = new Set<string>();
  async isRevoked(token: string) { return this.set.has(token); }
  async revoke(token: string) { this.set.add(token); }
}

export interface BuildServerOptions {
  googleClientId: string;
  googleBypass?: boolean; // DEV ONLY: allow bypassing online verification if Google is blocked
  allowedAudiences?: string[]; // optional: additional accepted audiences for bypass mode (e.g., Android client ID)
  revocations?: RevocationStore;
  jwtSecret?: string; // for password tokens
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

  const oauthClient = new OAuth2Client(opts.googleClientId);
  const revocations = opts.revocations ?? new InMemoryRevocationStore();
  const jwtSecret = opts.jwtSecret || process.env.PWD_JWT_SECRET || 'DEV_ONLY_CHANGE_ME';
  const users = buildUserStore();
  const content = buildContentStore();
  app.log.info({ event: 'user_store_selected', impl: users.constructor.name }, 'User store initialized');
  // ALTCHA configuration
  const ALTCHA_HMAC_KEY = process.env.ALTCHA_HMAC_KEY || process.env.PWD_JWT_SECRET || 'ALTCHA_DEV_ONLY_CHANGE_ME';
  const ALTCHA_MAX_NUMBER = Number(process.env.ALTCHA_MAX_NUMBER || 100000);
  const ALTCHA_EXPIRE_SECONDS = Number(process.env.ALTCHA_EXPIRE_SECONDS || 120); // 2 minutes default
  const ALTCHA_BYPASS = opts.altchaBypass ?? (process.env.NODE_ENV === 'test' && process.env.ALTCHA_DEV_ALLOW !== '0');

  app.get('/health', async () => ({ status: 'ok' }));

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

  app.post('/v1/auth/validate', async (req, reply) => {
    const bodySchema = z.object({ idToken: z.string().min(10) });
    const parse = bodySchema.safeParse((req as FastifyRequest).body);
    if (!parse.success) {
      app.log.warn({ event: 'auth_reject', reason: 'invalid_body', issues: parse.error.issues }, 'auth rejected: invalid body');
      reply.header('X-Auth-Reason', 'invalid_body');
      return reply.code(400).send({ error: 'invalid_body' });
    }
    const { idToken } = parse.data;
    try {
      app.log.info({ event: 'validate_start', googleBypass: !!opts.googleBypass }, 'auth validate start');
      let payload: TokenPayload | undefined;
  if (opts.googleBypass) {
        // DEV ONLY: decode without verifying signature (use only when Google endpoints are blocked)
        try {
          const parts = idToken.split('.');
          if (parts.length >= 2) {
            const json = Buffer.from(parts[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8');
            const parsed = JSON.parse(json);
            // basic audience check to reduce risk when bypassing
            if (parsed && typeof parsed === 'object') {
              const aud: unknown = (parsed as any).aud;
              const azp: unknown = (parsed as any).azp;
              const allowed = new Set<string>([opts.googleClientId, ...(opts.allowedAudiences ?? [])]);
              const audMatches = (typeof aud === 'string' && allowed.has(aud)) || (Array.isArray(aud) && aud.some(a => allowed.has(a)));
              const azpMatches = typeof azp === 'string' && allowed.has(azp);
              if (audMatches || azpMatches) {
                if (!audMatches && azpMatches) {
                  app.log.info({ event: 'google_bypass_accepted_via_azp', azp }, 'Accepted Google token via azp match');
                }
                if (audMatches) {
                  app.log.info({ event: 'google_bypass_accepted_via_aud', aud }, 'Accepted Google token via aud match');
                }
                payload = parsed as unknown as TokenPayload;
              } else {
                app.log.warn({ event: 'google_bypass_aud_mismatch', aud, azp, expectedAllowed: Array.from(allowed) }, 'ID token audience/azp do not match allowed audiences');
                app.log.warn({ event: 'auth_reject', reason: 'aud_mismatch', aud, azp }, 'auth rejected: audience mismatch');
                reply.header('X-Auth-Reason', 'aud_mismatch');
                return reply.code(401).send({ error: 'invalid_token' });
              }
            } else {
              app.log.warn({ event: 'google_bypass_invalid_payload' }, 'parsed token payload was not an object');
              app.log.warn({ event: 'auth_reject', reason: 'invalid_payload' }, 'auth rejected: invalid payload');
              reply.header('X-Auth-Reason', 'invalid_payload');
              return reply.code(401).send({ error: 'invalid_token' });
            }
          } else {
            app.log.warn({ event: 'google_bypass_invalid_parts' }, 'ID token did not contain 2 parts');
            app.log.warn({ event: 'auth_reject', reason: 'invalid_parts' }, 'auth rejected: invalid token parts');
            reply.header('X-Auth-Reason', 'invalid_parts');
            return reply.code(401).send({ error: 'invalid_token' });
          }
        } catch (e) {
          app.log.warn({ event: 'google_bypass_decode_failed', err: e }, 'google bypass decode failed');
          app.log.warn({ event: 'auth_reject', reason: 'decode_failed' }, 'auth rejected: decode failed');
          reply.header('X-Auth-Reason', 'decode_failed');
          return reply.code(401).send({ error: 'invalid_token' });
        }
      } else {
        const ticket = await oauthClient.verifyIdToken({ idToken, audience: opts.googleClientId });
        payload = ticket.getPayload() || undefined;
      }
      if (!payload) {
        app.log.warn({ event: 'validate_no_payload' }, 'no token payload');
        app.log.warn({ event: 'auth_reject', reason: 'no_payload' }, 'auth rejected: no payload');
        reply.header('X-Auth-Reason', 'no_payload');
        return reply.code(401).send({ error: 'invalid_token' });
      }
      // Derive a stable username: prefer email; else fallback to google:sub
      try {
        const sub = payload.sub;
        const email = payload.email && String(payload.email).trim().length ? String(payload.email) : undefined;
        if (!sub && !email) {
          app.log.warn({ event: 'google_validate_no_identity' }, 'google token missing sub/email');
          app.log.warn({ event: 'auth_reject', reason: 'no_identity' }, 'auth rejected: no identity');
          reply.header('X-Auth-Reason', 'no_identity');
          return reply.code(401).send({ error: 'invalid_token' });
        }
        const uname = (email ? email.toLowerCase() : `google:${sub}`);
        const existing = await users.findByUsername(uname);
        if (existing && (existing as any).deletedAt) {
          // Safety if any legacy soft-deleted row still exists
          app.log.warn({ event: 'google_validate_deleted', username: uname }, 'google user is deleted');
          reply.header('X-Auth-Reason', 'account_deleted');
          return reply.code(403).send({ error: 'account_deleted' });
        }
        const sentinel = 'GOOGLE_ONLY';
        const rec = await users.upsert(uname, sentinel);
        try { await users.updateLastLogin(rec.id); } catch (e) {
          app.log.warn({ event: 'google_validate_last_login_update_failed', username: uname, err: e }, 'failed to update last_login');
        }
        app.log.info({ event: 'google_validate_link', username: uname, action: existing ? 'existing' : 'created' }, 'google user linked');
      } catch (e) {
        app.log.warn({ event: 'google_validate_linkage_failed', err: e }, 'google validate user linkage failed');
      }
      return {
        sub: payload.sub,
        email: payload.email,
        name: payload.name,
        picture: payload.picture,
        iss: payload.iss,
        exp: payload.exp,
      };
    } catch (e) {
      app.log.warn({ event: 'validate_exception', err: e }, 'token verification failed');
      app.log.warn({ event: 'auth_reject', reason: 'exception' }, 'auth rejected: exception during validation');
      reply.header('X-Auth-Reason', 'exception');
      return reply.code(401).send({ error: 'invalid_token' });
    }
  });

  app.post('/v1/auth/revoke', async (req, reply) => {
    // Accept either { idToken } for Google or { token } for password JWT
    const bodySchema = z.union([
      z.object({ idToken: z.string().min(10) }),
      z.object({ token: z.string().min(10) })
    ]);
    const parsed = bodySchema.safeParse((req as FastifyRequest).body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_body' });
    // Only revoke our own password JWTs. Google ID tokens are short-lived and should not be locally blacklisted.
    if ('token' in parsed.data) {
      const tokenValue = parsed.data.token;
      await revocations.revoke(tokenValue);
      app.log.info({ event: 'revoke_pwd' }, 'password token revoked');
    } else {
      app.log.info({ event: 'revoke_google_ignored' }, 'ignored revoke for Google ID token');
    }
    return { revoked: true };
  });

  // ---- Username / Password Auth (username only, not email) ----
  app.post('/v1/auth/register', async (req, reply) => {
    const bodySchema = z.object({
      username: z.string().min(3).max(40).regex(/^[A-Za-z0-9_-]+$/),
      password: z.string().min(8).max(128),
      // ALTCHA payload submitted by client; Base64 JSON string
      altcha: z.string().min(20),
    });
    const parse = bodySchema.safeParse((req as FastifyRequest).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    const { username, password, altcha } = parse.data;
  app.log.info({ event: 'pwd_register_attempt', username }, 'password register attempt');
    // Verify ALTCHA payload
    const verified = ALTCHA_BYPASS ? true : await verifySolution(altcha, ALTCHA_HMAC_KEY, true);
    if (!verified) {
      app.log.warn({ event: 'altcha_failed', username }, 'ALTCHA verification failed');
      return reply.code(400).send({ error: 'altcha_failed' });
    }
    const hash = await bcrypt.hash(password, 12);
  const created = await users.create(username, hash);
    if (!created) return reply.code(409).send({ error: 'username_exists' });
    const token = jwt.sign({ sub: created.id, username: created.username, typ: 'pwd' }, jwtSecret, { expiresIn: '1h' });
  app.log.info({ event: 'pwd_register_success', username: created.username }, 'password register success');
    return { token, user: { id: created.id, username: created.username } };
  });

  app.post('/v1/auth/login', async (req, reply) => {
    const bodySchema = z.object({ username: z.string().min(3).max(40), password: z.string().min(1) });
    const parse = bodySchema.safeParse((req as FastifyRequest).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    const { username, password } = parse.data;
  app.log.info({ event: 'pwd_login_attempt', username }, 'password login attempt');
  const user = await users.findByUsername(username);
  if (!user) { app.log.warn({ event: 'pwd_login_invalid_user', username }, 'invalid username'); return reply.code(401).send({ error: 'invalid_credentials' }); }
  if ((user as any).deletedAt) { app.log.warn({ event: 'pwd_login_deleted_user', username }, 'deleted user login attempt'); return reply.code(403).send({ error: 'account_deleted' }); }
  // Block password login for Google-only accounts (sentinel hash)
  if (user.passwordHash === 'GOOGLE_ONLY') {
    app.log.warn({ event: 'pwd_login_disabled_google_only', username }, 'password login disabled for Google-linked account');
    return reply.code(403).send({ error: 'password_login_disabled' });
  }
    const ok = await bcrypt.compare(password, user.passwordHash);
  if (!ok) { app.log.warn({ event: 'pwd_login_bad_password', username }, 'bad password'); return reply.code(401).send({ error: 'invalid_credentials' }); }
    const token = jwt.sign({ sub: user.id, username: user.username, typ: 'pwd' }, jwtSecret, { expiresIn: '1h' });
  app.log.info({ event: 'pwd_login_success', username }, 'password login success');
  // record last login timestamp
  try { await users.updateLastLogin(user.id); } catch {}
    return { token, user: { id: user.id, username: user.username } };
  });

  app.post('/v1/auth/pwd/validate', async (req, reply) => {
  const bodySchema = z.object({ token: z.string().min(10) });
  const parse = bodySchema.safeParse((req as FastifyRequest).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    try {
  const token = parse.data.token;
  if (await revocations.isRevoked(token)) return reply.code(401).send({ error: 'revoked' });
  type PwdTokenPayload = { sub: string; username: string; typ: 'pwd'; exp: number };
  const decoded = jwt.verify(token, jwtSecret) as unknown as PwdTokenPayload;
    if (decoded.typ !== 'pwd') return reply.code(401).send({ error: 'wrong_type' });
  return { sub: decoded.sub, username: decoded.username, exp: decoded.exp };
    } catch (e) { return reply.code(401).send({ error: 'invalid_token' }); }
  });

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
