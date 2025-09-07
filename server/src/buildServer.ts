import Fastify, { FastifyInstance } from 'fastify';
import cors from '@fastify/cors';
import { OAuth2Client, TokenPayload } from 'google-auth-library';
import { z } from 'zod';

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
}

export function buildServer(opts: BuildServerOptions): FastifyInstance {
  const app = Fastify({ logger: true });
  app.register(cors, { origin: true });

  const oauthClient = new OAuth2Client(opts.googleClientId);
  const revocations = opts.revocations ?? new InMemoryRevocationStore();

  app.get('/health', async () => ({ status: 'ok' }));

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
    const bodySchema = z.object({ idToken: z.string().min(10) });
    const parse = bodySchema.safeParse((req as any).body);
    if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
    const { idToken } = parse.data;
    await revocations.revoke(idToken);
    return { revoked: true };
  });

  return app;
}
