import Fastify from 'fastify';
import cors from 'fastify-cors';

const app = Fastify({ logger: true });
app.register(cors, { origin: true });

app.get('/health', async () => ({ status: 'ok' }));

app.get('/articles', async () => {
  // placeholder: later fetch from storage
  return [{ id: 'sample', title: 'Sample Article', updatedAt: new Date().toISOString() }];
});

const port = Number(process.env.PORT || 8080);
app.listen({ port, host: '0.0.0.0' }).catch(err => {
  app.log.error(err);
  process.exit(1);
});