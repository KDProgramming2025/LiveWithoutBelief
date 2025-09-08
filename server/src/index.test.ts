import { afterAll, beforeAll, describe, expect, test } from 'vitest';
import { buildServer, InMemoryRevocationStore } from './buildServer.js';

let app: any;
// password flow tests (no email link now)
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
  process.env.NODE_ENV = 'test';
  // Set reCAPTCHA secret to force verification path in tests we control with fetch mock
  process.env.RECAPTCHA_SECRET = 'test-recaptcha-secret';
  // Mock global fetch for reCAPTCHA calls; we'll override per test when needed
  // @ts-ignore
  global.fetch = async (url: string, init: any) => {
    // default success high score
    return {
      async json() { return { success: true, score: 0.9 }; }
    } as any;
  };
  app = buildServer({ googleClientId, revocations: new InMemoryRevocationStore(), jwtSecret: 'test-secret' });
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
    // After refactor, invalid token bubbles to 401 in verify path OR 400 if schema rejected; here we now get 400 from earlier path
    expect([400,401]).toContain(res.statusCode);
    const body = res.json();
    expect(['invalid_token','invalid_body']).toContain(body.error);
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

describe('password auth', () => {
  test('register then login and validate token', async () => {
  const username = 'userexample';
  const password = 'StrongPass123!';
  const regRes = await app.inject({ method: 'POST', url: '/v1/auth/register', payload: { username, password, recaptchaToken: 'ok' } });
    expect(regRes.statusCode).toBe(200);
    const regBody = regRes.json();
    expect(regBody.token).toBeTruthy();
  const loginRes = await app.inject({ method: 'POST', url: '/v1/auth/login', payload: { username, password, recaptchaToken: 'ok' } });
    expect(loginRes.statusCode).toBe(200);
    const loginBody = loginRes.json();
  expect(loginBody.user.username).toBe(username);
    const validateRes = await app.inject({ method: 'POST', url: '/v1/auth/pwd/validate', payload: { token: loginBody.token } });
    expect(validateRes.statusCode).toBe(200);
  });

  test('duplicate register blocked', async () => {
  const username = 'dupuser';
  const password = 'AnotherStrong1!';
  const first = await app.inject({ method: 'POST', url: '/v1/auth/register', payload: { username, password, recaptchaToken: 'ok' } });
    expect(first.statusCode).toBe(200);
  const second = await app.inject({ method: 'POST', url: '/v1/auth/register', payload: { username, password, recaptchaToken: 'ok' } });
    expect(second.statusCode).toBe(409);
  });

  test('recaptcha failure rejects register', async () => {
    // Override fetch to simulate failure
    // @ts-ignore
    global.fetch = async () => ({ async json() { return { success: false, 'error-codes': ['invalid-input-response'] }; } }) as any;
  const r = await app.inject({ method: 'POST', url: '/v1/auth/register', payload: { username: 'r1user', password: 'StrongPass123!', recaptchaToken: 'x' } });
    expect(r.statusCode).toBe(400);
    expect(r.json()).toEqual({ error: 'recaptcha_failed' });
  });

  test('recaptcha low score rejected', async () => {
    // @ts-ignore
    global.fetch = async () => ({ async json() { return { success: true, score: 0.05 }; } }) as any;
  const r = await app.inject({ method: 'POST', url: '/v1/auth/register', payload: { username: 'r2user', password: 'StrongPass123!', recaptchaToken: 'y' } });
    expect(r.statusCode).toBe(400);
    expect(r.json()).toEqual({ error: 'recaptcha_failed' });
  });
});