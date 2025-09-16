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
const GOOGLE_CLIENT_IDS = (process.env.GOOGLE_CLIENT_IDS || '')
  .split(',')
  .map((s) => s.trim())
  .filter((s) => s.length > 0)

export const env = {
  PORT,
  ALTCHA_SECRET,
  GOOGLE_CLIENT_IDS,
}
