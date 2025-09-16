"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const vitest_1 = require("vitest");
const crypto_1 = __importDefault(require("crypto"));
const AltchaService_1 = require("../../src/services/AltchaService");
// Mirror client-side solver logic used by the Android app
function solveAltcha(algorithm, challenge, salt, max) {
    const algo = (algorithm.toLowerCase() === 'sha-1') ? 'sha1' : (algorithm.toLowerCase() === 'sha-512' ? 'sha512' : 'sha256');
    for (let n = 0; n <= max; n++) {
        const h = crypto_1.default.createHash(algo).update(salt + String(n)).digest('hex');
        if (h.startsWith(challenge))
            return n;
    }
    throw new Error(`not found up to ${max}`);
}
(0, vitest_1.describe)('AltchaService', () => {
    (0, vitest_1.it)('creates a challenge and verifies a valid solution payload', () => {
        const svc = new AltchaService_1.AltchaService();
        const ch = svc.createChallenge();
        // Find a solution like the app would
        const n = solveAltcha(ch.algorithm, ch.challenge, ch.salt, ch.maxnumber);
        const payload = Buffer.from(JSON.stringify({
            algorithm: ch.algorithm,
            challenge: ch.challenge,
            salt: ch.salt,
            number: n,
            signature: ch.signature,
        }), 'utf8').toString('base64');
        const res = svc.verifyPayload(payload);
        (0, vitest_1.expect)(res.ok).toBe(true);
    });
    (0, vitest_1.it)('rejects tampered signature', () => {
        const svc = new AltchaService_1.AltchaService();
        const ch = svc.createChallenge();
        const payload = Buffer.from(JSON.stringify({
            algorithm: ch.algorithm,
            challenge: ch.challenge,
            salt: ch.salt,
            number: 0,
            signature: 'deadbeef',
        }), 'utf8').toString('base64');
        const res = svc.verifyPayload(payload);
        (0, vitest_1.expect)(res.ok).toBe(false);
    });
});
// intentionally empty (TS test exists)
