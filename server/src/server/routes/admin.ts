import express from 'express'
import { AdminAuthService } from '../../services/AdminAuthService.js'

export const adminRouter = express.Router()
const auth = new AdminAuthService()

adminRouter.post('/login', async (req, res) => {
  const { username, password } = req.body || {}
  if (!username || !password) return res.status(400).json({ error: 'bad_request' })
  const ok = await auth.verifyPassword(username, password)
  if (!ok) return res.status(401).json({ error: 'unauthorized' })
  const token = auth.issueToken(username)
  res.json({ token })
})

export function requireAdmin(req: express.Request, res: express.Response, next: express.NextFunction) {
  const hdr = req.header('Authorization') || ''
  const m = hdr.match(/^Bearer\s+(.+)$/i)
  if (!m) return res.status(401).json({ error: 'unauthorized' })
  const session = auth.verifyToken(m[1])
  if (!session) return res.status(401).json({ error: 'unauthorized' })
  ;(req as any).admin = session
  next()
}
