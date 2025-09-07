import Fastify from 'fastify';
import cors from '@fastify/cors';
import { OAuth2Client, TokenPayload } from 'google-auth-library';
import { z } from 'zod';

export interface ArticleManifestItem {
  id: string;
  title: string;
  slug: string;
  version: number;
  updatedAt: string;
  wordCount: number;
}

const app = Fastify({ logger: true });
app.register(cors, { origin: true });

// Simple in-memory revocation store (swap with Redis / DB later)
const revokedTokens = new Set<string>();

const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID || 'CHANGE_ME';
const oauthClient = new OAuth2Client(GOOGLE_CLIENT_ID);

app.get('/health', async () => ({ status: 'ok' }));

app.get('/v1/articles/manifest', async () => {
  const now = new Date().toISOString();
  const items: ArticleManifestItem[] = [
    { id: 'sample-1', title: 'Sample Article 1', slug: 'sample-1', version: 1, updatedAt: now, wordCount: 600 },
    { id: 'sample-2', title: 'Sample Article 2', slug: 'sample-2', version: 1, updatedAt: now, wordCount: 750 }
  ];
  return items;
});

const port = Number(process.env.PORT || 8080);
app.listen({ port, host: '0.0.0.0' }).catch((err: unknown) => {
  // eslint-disable-next-line no-console
  console.error(err);
  process.exit(1);
});

// Validate ID token & return minimal profile
app.post('/v1/auth/validate', async (req: any, reply: any) => {
  const bodySchema = z.object({ idToken: z.string().min(10) });
  const parse = bodySchema.safeParse(req.body);
  if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
  const { idToken } = parse.data;
  if (revokedTokens.has(idToken)) return reply.code(401).send({ error: 'revoked' });
  try {
    const ticket = await oauthClient.verifyIdToken({ idToken, audience: GOOGLE_CLIENT_ID });
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
    req.log.warn({ err: e }, 'token verification failed');
    return reply.code(401).send({ error: 'invalid_token' });
  }
});

// Revoke id token (client sign-out). We just mark token string; real impl would blacklist jti or session id.
app.post('/v1/auth/revoke', async (req: any, reply: any) => {
  const bodySchema = z.object({ idToken: z.string().min(10) });
  const parse = bodySchema.safeParse(req.body);
  if (!parse.success) return reply.code(400).send({ error: 'invalid_body' });
  const { idToken } = parse.data;
  revokedTokens.add(idToken);
  return { revoked: true };
});