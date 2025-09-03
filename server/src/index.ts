import Fastify from 'fastify';
import cors from '@fastify/cors';

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