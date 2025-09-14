import type { FastifyInstance, FastifyRequest } from 'fastify';
import { z } from 'zod';
import type { AuthService } from './service.js';

export function registerAuthRoutes(app: FastifyInstance, auth: AuthService) {
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
}
