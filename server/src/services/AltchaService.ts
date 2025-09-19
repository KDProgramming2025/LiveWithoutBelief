import crypto from 'node:crypto'

export type AltchaChallenge = {
  algorithm: 'SHA-256' | 'SHA-1' | 'SHA-512'
  challenge: string
  salt: string
  maxnumber: number
  signature: string
}

export class AltchaService {
  private readonly secret: string
  private readonly defaultAlgorithm: AltchaChallenge['algorithm']
  private readonly challengePrefixLen: number
  private readonly defaultMax: number

  constructor(options?: {
    secret?: string
    algorithm?: AltchaChallenge['algorithm']
    challengePrefixLen?: number
    maxnumber?: number
  }) {
    this.secret = options?.secret ?? process.env.ALTCHA_SECRET ?? 'dev-secret'
    this.defaultAlgorithm = options?.algorithm ?? 'SHA-256'
    // Keep difficulty small so unit tests solve quickly
    this.challengePrefixLen = Math.max(2, Math.min(6, options?.challengePrefixLen ?? 3))
    this.defaultMax = options?.maxnumber ?? 200_000
  }

  createChallenge(): AltchaChallenge {
    const algorithm = this.defaultAlgorithm
    const salt = crypto.randomBytes(12).toString('hex')
    const challenge = crypto.randomBytes(16).toString('hex').slice(0, this.challengePrefixLen)
    const maxnumber = this.defaultMax
    const signature = this.sign({ algorithm, challenge, salt, maxnumber })
    return { algorithm, challenge, salt, maxnumber, signature }
  }

  verifyPayload(payloadBase64: string): { ok: boolean } {
    try {
      const json = Buffer.from(payloadBase64, 'base64').toString('utf8')
      const p = JSON.parse(json) as {
        algorithm: string
        challenge: string
        salt: string
        number: number
        signature: string
      }
      // Verify signature first
      const expectedSig = this.sign({
        algorithm: this.normalizeAlgName(p.algorithm) as AltchaChallenge['algorithm'],
        challenge: p.challenge,
        salt: p.salt,
        maxnumber: typeof p.number === 'number' && Number.isFinite(p.number) ? this.defaultMax : this.defaultMax,
      })
      if (!timingSafeEqualHex(expectedSig, String(p.signature))) return { ok: false }

      // Verify solution
      const algo = this.nodeHashName(p.algorithm)
      const digest = crypto.createHash(algo).update(p.salt + String(p.number)).digest('hex')
      return { ok: digest.startsWith(p.challenge) }
    } catch {
      return { ok: false }
    }
  }

  private sign(input: { algorithm: AltchaChallenge['algorithm']; challenge: string; salt: string; maxnumber: number }): string {
    const payload = `${input.algorithm}|${input.challenge}|${input.salt}|${input.maxnumber}`
    return crypto.createHmac('sha256', this.secret).update(payload).digest('hex')
  }

  private normalizeAlgName(a: string): AltchaChallenge['algorithm'] {
    const v = a.toUpperCase()
    if (v === 'SHA-1' || v === 'SHA1') return 'SHA-1'
    if (v === 'SHA-512' || v === 'SHA512') return 'SHA-512'
    return 'SHA-256'
  }

  private nodeHashName(a: string): 'sha1' | 'sha256' | 'sha512' {
    const v = a.toLowerCase()
    if (v === 'sha-1' || v === 'sha1') return 'sha1'
    if (v === 'sha-512' || v === 'sha512') return 'sha512'
    return 'sha256'
  }
}

function timingSafeEqualHex(a: string, b: string): boolean {
  try {
    const ba = Buffer.from(a, 'hex')
    const bb = Buffer.from(b, 'hex')
    return ba.length === bb.length && crypto.timingSafeEqual(ba, bb)
  } catch {
    return false
  }
}
// Removed: custom ALTCHA implementation; replaced by official altcha library usage in routes.
