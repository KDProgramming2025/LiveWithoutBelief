import type { FastifyInstance, FastifyRequest } from 'fastify';
import { z } from 'zod';
import type { AuthService } from './service.js';
import bcrypt from 'bcryptjs';

export function registerAuthRoutes(app: FastifyInstance, auth: AuthService) {
  // Simple register endpoint based on email only
  app.post('/v1/auth/register', async (req, reply) => {
    const schema = z.object({ email: z.string().email() });
    const parsed = schema.safeParse((req as FastifyRequest).body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_body' });
    try {
      const { user, created } = await auth.registerByEmail(parsed.data.email);
      return reply.code(created ? 201 : 200).send({ user });
    } catch (e: any) {
      return reply.code(500).send({ error: 'server_error' });
    }
  });

  // Minimal Google sign-in endpoint
  app.post('/v1/auth/google', async (req, reply) => {
    const schema = z.object({ idToken: z.string().min(10) });
    const parsed = schema.safeParse((req as FastifyRequest).body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_body' });
    try {
      const { user, profile, created } = await auth.signInWithGoogle(parsed.data.idToken);
      return reply.code(created ? 201 : 200).send({ user, profile });
    } catch (e: any) {
      const reason = e?.code ?? 'invalid_token';
      reply.header('X-Auth-Reason', reason);
      return reply.code(reason === 'aud_mismatch' ? 401 : 400).send({ error: String(reason) });
    }
  });

  // Simple whoami (for future cookie/JWT session; placeholder)
  app.get('/v1/auth/me', async (_req, reply) => {
    return reply.code(501).send({ error: 'not_implemented' });
  });

  // Password-based registration
  app.post('/v1/auth/pwd/register', async (req, reply) => {
    const schema = z.object({ username: z.string().min(3).max(80).email().or(z.string().min(3).max(80)), password: z.string().min(8).max(128) });
    const parsed = schema.safeParse((req as FastifyRequest).body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_body' });
    try {
      const hash = await bcrypt.hash(parsed.data.password, 10);
      const user = await auth.registerWithPassword(parsed.data.username, hash);
      return reply.code(200).send({ user });
    } catch (e: any) {
      if (e?.code === 'user_exists') return reply.code(409).send({ error: 'user_exists' });
      return reply.code(500).send({ error: 'server_error' });
    }
  });

  // Password-based login
  app.post('/v1/auth/pwd/login', async (req, reply) => {
    const schema = z.object({ username: z.string().min(3).max(80), password: z.string().min(1) });
    const parsed = schema.safeParse((req as FastifyRequest).body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_body' });
    try {
      const ok = await auth.verifyPassword(parsed.data.username, parsed.data.password);
      if (!ok) return reply.code(401).send({ error: 'invalid_credentials' });
      const user = await auth.ensureUser(parsed.data.username);
      return reply.code(200).send({ user });
    } catch {
      return reply.code(500).send({ error: 'server_error' });
    }
  });
}
