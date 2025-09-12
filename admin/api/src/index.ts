import Fastify, { FastifyInstance } from 'fastify';
import cors from '@fastify/cors';

export function buildServer(): FastifyInstance {
  const server = Fastify({ logger: true });
  // CORS for local dev web UI
  server.register(cors, { origin: true });

  // Mock queue status
  server.get('/v1/admin/ingestion/queue', async () => {
    return {
      pending: 2,
      running: 1,
      failed: 0,
      items: [
        { id: 'q1', article: 'docx-001', status: 'running', startedAt: new Date().toISOString() },
        { id: 'q2', article: 'docx-002', status: 'pending' },
        { id: 'q3', article: 'docx-003', status: 'pending' }
      ]
    };
  });

  // Mock user support view (redacted)
  server.get('/v1/admin/support/users', async () => {
    return [
      { id: 'u1', email: 'user1@example.com', issues: 1 },
      { id: 'u2', email: 'user2@example.com', issues: 0 }
    ];
  });

  return server;
}
