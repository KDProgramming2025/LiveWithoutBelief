import 'dotenv/config';
import { buildServer } from './buildServer.js';

const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID || 'CHANGE_ME';
const PORT = Number(process.env.PORT || process.env.SERVER_API_PORT || 8080);
const HOST = process.env.HOST || '0.0.0.0';

const app = buildServer({ googleClientId: GOOGLE_CLIENT_ID });

app.listen({ port: PORT, host: HOST }).then(() => {
  app.log.info({ port: PORT }, 'server started');
}).catch((err) => {
  app.log.error({ err }, 'failed to start');
  process.exit(1);
});
