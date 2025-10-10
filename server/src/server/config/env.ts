import dotenv from 'dotenv'
import path from 'node:path'

// Resolve env file in this order:
// 1) LWB_ENV_FILE (e.g., /etc/lwb-server.env on Linux)
// 2) Project root .env (../../.env when running from server/)
// 3) server/.env.local (dev default)
const guessedProjectRootEnv = path.resolve(process.cwd(), '..', '.env')
const envFile = process.env.LWB_ENV_FILE || (process.env.NODE_ENV === 'production' ? guessedProjectRootEnv : path.resolve(process.cwd(), '.env.local'))
dotenv.config({ path: envFile })

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
