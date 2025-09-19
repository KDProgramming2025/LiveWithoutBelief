import { describe, it, expect } from 'vitest'
import crypto from 'crypto'
import { createChallenge, verifySolution } from 'altcha-lib'
import { env } from '../../src/server/config/env'

// Mirror client-side solver logic used by the Android app
function solveAltcha(algorithm: string, challenge: string, salt: string, max: number): number {
  const algo = (algorithm.toLowerCase() === 'sha-1') ? 'sha1' : (algorithm.toLowerCase() === 'sha-512' ? 'sha512' : 'sha256')
  for (let n = 0; n <= max; n++) {
    const h = crypto.createHash(algo).update(salt + String(n)).digest('hex')
    if (h.startsWith(challenge)) return n
  }
  throw new Error(`not found up to ${max}`)
}

describe('ALTCHA integration (official lib)', () => {
  it('creates a challenge and verifies a valid solution payload', async () => {
    const ch = await createChallenge({ hmacKey: env.ALTCHA_SECRET, maxnumber: 50_000 })
    const n = solveAltcha(ch.algorithm, ch.challenge, ch.salt, ch.maxnumber ?? 1_000_000)
    const payload = Buffer.from(JSON.stringify({
      algorithm: ch.algorithm,
      challenge: ch.challenge,
      salt: ch.salt,
      number: n,
      signature: ch.signature,
    }), 'utf8').toString('base64')
    const ok = await verifySolution(payload, env.ALTCHA_SECRET)
    expect(ok).toBe(true)
  })

  it('rejects tampered signature', async () => {
    const ch = await createChallenge({ hmacKey: env.ALTCHA_SECRET, maxnumber: 50_000 })
    const payload = Buffer.from(JSON.stringify({
      algorithm: ch.algorithm,
      challenge: ch.challenge,
      salt: ch.salt,
      number: 0,
      signature: 'deadbeef',
    }), 'utf8').toString('base64')
    const ok = await verifySolution(payload, env.ALTCHA_SECRET)
    expect(ok).toBe(false)
  })
})
