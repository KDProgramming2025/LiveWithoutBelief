import { afterAll, beforeAll, expect, test } from 'vitest';
import Fastify from 'fastify';
import cors from '@fastify/cors';

let app: ReturnType<typeof Fastify>;

beforeAll(async () => {
  app = Fastify();
  app.register(cors, { origin: true });
  app.get('/health', async () => ({ status: 'ok' }));
  await app.ready();
});

afterAll(async () => {
  await app.close();
});

test('health endpoint returns ok', async () => {
  const res = await app.inject({ method: 'GET', url: '/health' });
  expect(res.statusCode).toBe(200);
  expect(res.json()).toEqual({ status: 'ok' });
});