import express, { Router } from 'express'
import { createChallenge } from 'altcha-lib'
import { env } from '../config/env.js'

export const altchaRouter = Router()

// Minimal challenge endpoint consumable by the widget via challengeurl
altchaRouter.get('/challenge', async (_req: express.Request, res: express.Response) => {
  const challenge = await createChallenge({ hmacKey: env.ALTCHA_SECRET })
  res.json(challenge)
})
// Removed: custom ALTCHA challenge route; use official altcha puzzle instead.
