import { afterAll, beforeAll, describe, expect, test } from 'vitest';
import { buildServer, InMemoryRevocationStore } from './buildServer.js';

let app: any;
const googleClientId = 'test-client';

// naive stub of google-auth-library by monkey-patching global import cache
// We rely on the real module inside buildServer, so we monkey patch its prototype method at runtime.
import * as googleAuth from 'google-auth-library';

class FakeTicket {
  constructor(private payload: any) {}
  getPayload() { return this.payload; }
}

beforeAll(async () => {
  // Patch verifyIdToken to return canned payload for specific tokens
  // @ts-ignore
  googleAuth.OAuth2Client.prototype.verifyIdToken = async function ({ idToken, audience }: any) {
    if (audience !== googleClientId) throw new Error('bad_audience');
    if (idToken === 'good-token') {
      return new FakeTicket({ sub: 'uid123', email: 'u@example.com', name: 'U Test', picture: 'http://x/y.png', iss: 'https://accounts.google.com', exp: Math.floor(Date.now()/1000)+3600 });
    }
    throw new Error('invalid token');
  };
  app = buildServer({ googleClientId, revocations: new InMemoryRevocationStore() });
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

describe('auth validate', () => {
  test('valid token', async () => {
    const res = await app.inject({ method: 'POST', url: '/v1/auth/validate', payload: { idToken: 'good-token' } });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.email).toBe('u@example.com');
  });

  test('invalid token', async () => {
    const res = await app.inject({ method: 'POST', url: '/v1/auth/validate', payload: { idToken: 'bad-token' } });
    expect(res.statusCode).toBe(401);
    expect(res.json()).toEqual({ error: 'invalid_token' });
  });
});

describe('auth revoke', () => {
  test('revoke then validate gets revoked', async () => {
    const revokeRes = await app.inject({ method: 'POST', url: '/v1/auth/revoke', payload: { idToken: 'good-token' } });
    expect(revokeRes.statusCode).toBe(200);
    expect(revokeRes.json()).toEqual({ revoked: true });
    const validateAfter = await app.inject({ method: 'POST', url: '/v1/auth/validate', payload: { idToken: 'good-token' } });
    expect(validateAfter.statusCode).toBe(401);
    expect(validateAfter.json()).toEqual({ error: 'revoked' });
  });
});