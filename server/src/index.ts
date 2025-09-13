// Load env from /etc/lwb-server.env if present, then fallback to project .env
import fs from 'fs';
import path from 'path';
import dotenv from 'dotenv';
try {
  if (fs.existsSync('/etc/lwb-server.env')) {
    dotenv.config({ path: '/etc/lwb-server.env' });
  }
} catch {}
try {
  const localEnv = path.join(process.cwd(), '.env');
  if (fs.existsSync(localEnv)) {
    dotenv.config({ path: localEnv });
  }
} catch {}
import { buildServer } from './buildServer.js';

const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID || 'CHANGE_ME';
const PORT = Number(process.env.PORT || process.env.SERVER_API_PORT || 8080);
const HOST = process.env.HOST || '0.0.0.0';

const GOOGLE_BYPASS = process.env.GOOGLE_CERTS_BYPASS === '1';
// Optional list of additional accepted audiences in bypass mode (comma-separated), e.g., Android client ID(s)
const GOOGLE_ALLOWED_AUDIENCES = (process.env.GOOGLE_ALLOWED_AUDIENCES || '')
  .split(',')
  .map(s => s.trim())
  .filter(Boolean);
const app = buildServer({ googleClientId: GOOGLE_CLIENT_ID, googleBypass: GOOGLE_BYPASS, allowedAudiences: GOOGLE_ALLOWED_AUDIENCES });

app.listen({ port: PORT, host: HOST }).then(() => {
  app.log.info({ port: PORT }, 'server started');
}).catch((err) => {
  app.log.error({ err }, 'failed to start');
  process.exit(1);
});
