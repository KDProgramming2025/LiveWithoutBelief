import { buildServer } from './index';

const server = buildServer();
const port = Number(process.env.ADMIN_API_PORT || '5050');
const host = process.env.ADMIN_API_HOST || '0.0.0.0';

process.on('unhandledRejection', (err) => {
  server.log.error({ err }, 'Unhandled promise rejection');
});

server
  .listen({ port, host })
  .then(() => server.log.info({ host, port }, 'LWB Admin API listening'))
  .catch((err) => {
    server.log.error(err);
    process.exit(1);
  });
