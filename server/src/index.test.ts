import { afterAll, beforeAll, describe, expect, test, vi } from 'vitest';
import { buildServer } from './buildServer.js';
import type { ArticleManifestItem } from './buildServer.js';
import type { FastifyInstance } from 'fastify';

let app: FastifyInstance;
const googleClientId = 'test-client';

// Mock ingestion module globally for ESM to avoid heavy processing during tests
vi.mock('./ingestion/docx.js', () => ({
  parseDocx: async () => ({ sections: [{ kind: 'heading', level: 1, text: 'T' }, { kind: 'paragraph', text: 'Hello' }], media: [], wordCount: 3, html: '<h1>T</h1><p>Hello</p>', text: 'T Hello' })
}));

// naive stub of google-auth-library by monkey-patching global import cache
// We rely on the real module inside buildServer, so we monkey patch its prototype method at runtime.
import * as googleAuth from 'google-auth-library';

class FakeTicket {
  constructor(private payload: Record<string, unknown>) {}
  // Casting here is acceptable for a test double
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getPayload() { return this.payload as any; }
}

beforeAll(async () => {
  // Patch verifyIdToken to return canned payload for specific tokens
  // @ts-expect-error override for tests
  googleAuth.OAuth2Client.prototype.verifyIdToken = async function ({ idToken, audience }: { idToken: string; audience: string }) {
    if (audience !== googleClientId) throw new Error('bad_audience');
    if (idToken === 'good-token') {
      return new FakeTicket({ sub: 'uid123', email: 'u@example.com', name: 'U Test', picture: 'http://x/y.png', iss: 'https://accounts.google.com', exp: Math.floor(Date.now()/1000)+3600 });
    }
    throw new Error('invalid token');
  };
  process.env.NODE_ENV = 'test';
  // Enable ALTCHA bypass for tests
  app = buildServer({ googleClientId, altchaBypass: true });
  await app.ready();
});

afterAll(async () => { await app.close(); });

describe('health', () => {
  test('returns ok', async () => {
    const res = await app.inject({ method: 'GET', url: '/health' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ status: 'ok' });
  });
});

describe('auth google sign-in', () => {
  test('valid token creates user', async () => {
    const res = await app.inject({ method: 'POST', url: '/v1/auth/google', payload: { idToken: 'good-token' } });
    expect([200,201]).toContain(res.statusCode);
    const body = res.json();
    expect(body.user.username).toBe('u@example.com');
    expect(body.profile.email).toBe('u@example.com');
  });

  test('invalid token rejected', async () => {
    const res = await app.inject({ method: 'POST', url: '/v1/auth/google', payload: { idToken: 'bad-token' } });
    expect([400,401]).toContain(res.statusCode);
    const body = res.json();
    expect(body.error).toBeTruthy();
  });
});

// Password flow removed in simplified design

// Revoke/password flows removed

// Password auth tests removed

describe('ingestion endpoint', () => {
  test('uploads a docx and returns summary', async () => {
    const boundary = '----lwb-boundary';
    const payload =
      `--${boundary}\r\n` +
      `Content-Disposition: form-data; name="file"; filename="t.docx"\r\n` +
      `Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n` +
      `dummy\r\n` +
      `--${boundary}--\r\n`;
    const res = await app.inject({
      method: 'POST', url: '/v1/ingest/docx',
      payload, headers: { 'content-type': `multipart/form-data; boundary=${boundary}` }
    });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.wordCount).toBe(3);
  expect(body.sections).toBe(2);
    // manifest is optional based on env
    if (body.manifest) {
      expect(body.manifest.checksum).toBeTruthy();
      expect(body.manifest.signature).toBeTruthy();
    }
  });

  test('after ingest, manifests list includes the article and article is retrievable', async () => {
    // list manifests
    const list = await app.inject({ method: 'GET', url: '/v1/articles/manifest' });
    expect(list.statusCode).toBe(200);
    const listBody = list.json();
    expect(Array.isArray(listBody.items)).toBe(true);
  const item = listBody.items.find((it: ArticleManifestItem) => it.id === 't' || it.slug === 't');
    // depending on slug derivation, it should include something similar
    expect(listBody.items.length).toBeGreaterThan(0);
    // fetch first item
    const id = (item?.id) ?? listBody.items[0].id;
    const get = await app.inject({ method: 'GET', url: `/v1/articles/${id}` });
    expect(get.statusCode).toBe(200);
    const article = get.json();
    expect(article.id).toBeTruthy();
    expect(article.sections?.length).toBeGreaterThan(0);
    expect(article.wordCount).toBe(3);
  });
});

describe('auth register by email', () => {
  test('creates then idempotent', async () => {
    const r1 = await app.inject({ method: 'POST', url: '/v1/auth/register', payload: { email: 'newuser@example.com' } });
    expect([200,201]).toContain(r1.statusCode);
    const u1 = r1.json();
    expect(u1.user.username).toBe('newuser@example.com');
    const r2 = await app.inject({ method: 'POST', url: '/v1/auth/register', payload: { email: 'newuser@example.com' } });
    expect([200,201]).toContain(r2.statusCode);
    const u2 = r2.json();
    expect(u2.user.id).toBe(u1.user.id);
  });
});