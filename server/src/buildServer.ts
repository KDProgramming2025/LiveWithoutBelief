import Fastify, { FastifyInstance } from 'fastify';
import cors from '@fastify/cors';
import { OAuth2Client, TokenPayload } from 'google-auth-library';
import { z } from 'zod';
import crypto from 'crypto'; // still used for revocation store IDs, etc.
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { buildUserStore } from './userStore.js';
import { createChallenge, verifySolution, extractParams } from 'altcha-lib';

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
  revocations?: RevocationStore;
  jwtSecret?: string; // for password tokens
}


export function buildServer(opts: BuildServerOptions): FastifyInstance {
  const app = Fastify({ logger: true });
  app.register(cors, { origin: true });

  // Override Fastify default JSON parser to capture raw body & diagnose production failures.
  try { app.removeContentTypeParser('application/json'); } catch (_) { /* ignore */ }
  app.addContentTypeParser('application/json', { parseAs: 'string' }, (req, body, done) => {
    const text = body as string;
    (req as any).rawBody = text;
    try {
      const parsed = text && text.trim().length ? JSON.parse(text) : {};
      done(null, parsed);
    } catch (err: any) {
      app.log.error({ err, event: 'json_parse_error', rawLength: text?.length, bodySnippet: text.slice(0, 200) }, 'failed to parse JSON body');
      done(err);
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
  app.log.info({ event: 'user_store_selected', impl: users.constructor.name }, 'User store initialized');
  // ALTCHA configuration
  const ALTCHA_HMAC_KEY = process.env.ALTCHA_HMAC_KEY || process.env.PWD_JWT_SECRET || 'ALTCHA_DEV_ONLY_CHANGE_ME';
  const ALTCHA_MAX_NUMBER = Number(process.env.ALTCHA_MAX_NUMBER || 100000);
  const ALTCHA_EXPIRE_SECONDS = Number(process.env.ALTCHA_EXPIRE_SECONDS || 120); // 2 minutes default

  app.get('/health', async () => ({ status: 'ok' }));

  // ALTCHA challenge endpoint for clients (Android WebView or in-app fetch)
  app.get('/v1/altcha/challenge', async (req, reply) => {
    const nowSec = Math.floor(Date.now() / 1000);
    const expires = nowSec + ALTCHA_EXPIRE_SECONDS;
    // Encode expires in the salt per ALTCHA guidance
    const saltParam = `expires=${expires}`;
    const challenge = await createChallenge({ hmacKey: ALTCHA_HMAC_KEY, maxNumber: ALTCHA_MAX_NUMBER, params: { expires: String(expires) } });
    // Return challenge to client
    return reply.code(200).send(challenge);
  });

  app.get('/v1/articles/manifest', async () => {
    const now = new Date().toISOString();
    const items: ArticleManifestItem[] = [
      { id: 'sample-1', title: 'Sample Article 1', slug: 'sample-1', version: 1, updatedAt: now, wordCount: 600 },
      { id: 'sample-2', title: 'Sample Article 2', slug: 'sample-2', version: 1, updatedAt: now, wordCount: 750 }
    ];
    return items;
  });

  app.post('/v1/auth/validate', async (req, reply) => {
    const bodySchema = z.object({ idToken: z.string().min(10) });
    const parse = bodySchema.safeParse((req as any).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    const { idToken } = parse.data;
    if (await revocations.isRevoked(idToken)) return reply.code(401).send({ error: 'revoked' });
    try {
      const ticket = await oauthClient.verifyIdToken({ idToken, audience: opts.googleClientId });
      const payload: TokenPayload | undefined = ticket.getPayload();
      if (!payload) return reply.code(401).send({ error: 'invalid_token' });
      return {
        sub: payload.sub,
        email: payload.email,
        name: payload.name,
        picture: payload.picture,
        iss: payload.iss,
        exp: payload.exp,
      };
    } catch (e) {
      (req as any).log?.warn({ err: e }, 'token verification failed');
      return reply.code(401).send({ error: 'invalid_token' });
    }
  });

  app.post('/v1/auth/revoke', async (req, reply) => {
  // Accept either { idToken } for Google or { token } for password JWT
  const body: any = (req as any).body || {};
  let tokenValue: string | undefined = undefined;
  if (typeof body.idToken === 'string') tokenValue = body.idToken;
  else if (typeof body.token === 'string') tokenValue = body.token;
  if (!tokenValue || tokenValue.length < 10) return reply.code(400).send({ error: 'invalid_body' });
  await revocations.revoke(tokenValue);
  return { revoked: true };
  });

  // ---- Username / Password Auth (username only, not email) ----
  app.post('/v1/auth/register', async (req, reply) => {
    const bodySchema = z.object({
      username: z.string().min(3).max(40).regex(/^[A-Za-z0-9_\-]+$/),
      password: z.string().min(8).max(128),
      // ALTCHA payload submitted by client; Base64 JSON string
      altcha: z.string().min(20),
    });
    const parse = bodySchema.safeParse((req as any).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    const { username, password, altcha } = parse.data;
  app.log.info({ event: 'pwd_register_attempt', username }, 'password register attempt');
    // Verify ALTCHA payload
    const verified = await verifySolution(altcha, ALTCHA_HMAC_KEY, true);
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
    const bodySchema = z.object({ username: z.string().min(3).max(40), password: z.string().min(1), recaptchaToken: z.string().optional() });
    const parse = bodySchema.safeParse((req as any).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    const { username, password, recaptchaToken } = parse.data;
  app.log.info({ event: 'pwd_login_attempt', username }, 'password login attempt');
  const user = await users.findByUsername(username);
  if (!user) { app.log.warn({ event: 'pwd_login_invalid_user', username }, 'invalid username'); return reply.code(401).send({ error: 'invalid_credentials' }); }
    const ok = await bcrypt.compare(password, user.passwordHash);
  if (!ok) { app.log.warn({ event: 'pwd_login_bad_password', username }, 'bad password'); return reply.code(401).send({ error: 'invalid_credentials' }); }
    const token = jwt.sign({ sub: user.id, username: user.username, typ: 'pwd' }, jwtSecret, { expiresIn: '1h' });
  app.log.info({ event: 'pwd_login_success', username }, 'password login success');
    return { token, user: { id: user.id, username: user.username } };
  });

  app.post('/v1/auth/pwd/validate', async (req, reply) => {
    const bodySchema = z.object({ token: z.string().min(10) });
    const parse = bodySchema.safeParse((req as any).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    try {
  const token = parse.data.token;
  if (await revocations.isRevoked(token)) return reply.code(401).send({ error: 'revoked' });
  const decoded: any = jwt.verify(token, jwtSecret);
      if (decoded.typ !== 'pwd') return reply.code(401).send({ error: 'wrong_type' });
  return { sub: decoded.sub, username: decoded.username, exp: decoded.exp };
    } catch (e) { return reply.code(401).send({ error: 'invalid_token' }); }
  });

  return app;
}
