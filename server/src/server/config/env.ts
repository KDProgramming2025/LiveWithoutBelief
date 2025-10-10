import dotenv from 'dotenv'
import path from 'node:path'

// Resolve env file with precedence:
// 1) Explicit LWB_ENV_FILE (e.g., /etc/lwb-server.env) when provided
// 2) In production, do not auto-load any file; rely on process env (systemd EnvironmentFile)
// 3) In development, load server/.env.local
const isProd = process.env.NODE_ENV === 'production'
const explicitEnv = process.env.LWB_ENV_FILE
if (explicitEnv) {
  dotenv.config({ path: explicitEnv })
} else if (!isProd) {
  dotenv.config({ path: path.resolve(process.cwd(), '.env.local') })
}

const PORT = Number(process.env.PORT || 4433)
const ALTCHA_SECRET = process.env.ALTCHA_SECRET || 'dev-secret'
const ALTCHA_MAXNUMBER = process.env.ALTCHA_MAXNUMBER ? Number(process.env.ALTCHA_MAXNUMBER) : undefined
const ADMIN_USER = process.env.ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || ''
const ADMIN_PASSWORD_HASH = process.env.ADMIN_PASSWORD_HASH || ''
const ADMIN_JWT_SECRET = process.env.ADMIN_JWT_SECRET || (process.env.ALTCHA_SECRET ?? 'dev-secret')
const GOOGLE_CLIENT_IDS = (process.env.GOOGLE_CLIENT_IDS || '')
  .split(',')
  .map((s) => s.trim())
  .filter((s) => s.length > 0)

export const env = {
  PORT,
  ALTCHA_SECRET,
  ALTCHA_MAXNUMBER,
  GOOGLE_CLIENT_IDS,
  ADMIN_USER,
  ADMIN_PASSWORD,
  ADMIN_PASSWORD_HASH,
  ADMIN_JWT_SECRET,
  APP_SERVER_HOST: process.env.APP_SERVER_HOST,
  APP_SERVER_SCHEME: process.env.APP_SERVER_SCHEME || 'https',
}
