import { OAuth2Client, TokenPayload } from 'google-auth-library';
import type { AuthResult, GoogleProfile } from './domain.js';
import type { UserRepository } from './repository.js';
import bcrypt from 'bcryptjs';

export type AuthServiceOptions = {
  googleClientId: string;
  googleBypass?: boolean;
  allowedAudiences?: string[];
};

export class AuthService {
  private oauth: OAuth2Client;
  constructor(private repo: UserRepository, private opts: AuthServiceOptions) {
    this.oauth = new OAuth2Client(opts.googleClientId);
  }

  async registerByEmail(email: string): Promise<{ user: { id: string; username: string; createdAt: string; lastLogin?: string }; created: boolean }> {
    const username = email.toLowerCase();
    const existing = await this.repo.findByUsername(username);
    const { user, created } = existing
      ? { user: existing, created: false }
      : await this.repo.upsertByUsername(username);
    await this.repo.touchLastLogin(user.id);
    return { user, created };
  }

  async signInWithGoogle(idToken: string): Promise<AuthResult> {
    const payload = await this.verifyGoogleToken(idToken);
    const username = (payload.email || payload.sub || '').toLowerCase();
    if (!username) throw Object.assign(new Error('no_identity'), { code: 'no_identity' });
    const existing = await this.repo.findByUsername(username);
    const { user, created } = existing
      ? { user: existing, created: false }
      : await this.repo.upsertByUsername(username);
    await this.repo.touchLastLogin(user.id);
    const profile: GoogleProfile = {
      sub: payload.sub,
      email: payload.email,
      name: payload.name,
      picture: payload.picture,
      iss: payload.iss,
      exp: payload.exp,
    };
  return { user, profile, created };
  }

  private async verifyGoogleToken(idToken: string): Promise<TokenPayload> {
    if (this.opts.googleBypass) {
      const parts = idToken.split('.');
      if (parts.length < 2) throw Object.assign(new Error('invalid_parts'), { code: 'invalid_parts' });
      const json = Buffer.from(parts[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8');
      const payload = JSON.parse(json);
      const allowed = new Set([this.opts.googleClientId, ...(this.opts.allowedAudiences ?? [])]);
      const aud = payload?.aud;
      const azp = payload?.azp;
      const audOk = (typeof aud === 'string' && allowed.has(aud)) || (Array.isArray(aud) && aud.some((a: string) => allowed.has(a)));
      const azpOk = typeof azp === 'string' && allowed.has(azp);
      if (!audOk && !azpOk) throw Object.assign(new Error('aud_mismatch'), { code: 'aud_mismatch' });
      return payload as TokenPayload;
    }
    const ticket = await this.oauth.verifyIdToken({ idToken, audience: this.opts.googleClientId });
    const payload = ticket.getPayload();
    if (!payload) throw Object.assign(new Error('no_payload'), { code: 'no_payload' });
    return payload;
  }

  // Password-based APIs
  async registerWithPassword(username: string, passwordHash: string) {
    const uname = username.toLowerCase();
    const existing = await this.repo.findByUsername(uname);
    if (existing) throw Object.assign(new Error('user_exists'), { code: 'user_exists' });
    const user = await this.repo.createUserWithPassword(uname, passwordHash);
    await this.repo.touchLastLogin(user.id);
    return user;
  }

  async verifyPassword(username: string, password: string): Promise<boolean> {
    const uname = username.toLowerCase();
    const hash = await this.repo.getPasswordHash(uname);
    if (!hash) return false;
    try { return await bcrypt.compare(password, hash); } catch { return false; }
  }

  async ensureUser(username: string) {
    const uname = username.toLowerCase();
    const existing = await this.repo.findByUsername(uname);
    if (existing) { await this.repo.touchLastLogin(existing.id); return existing; }
    const { user } = await this.repo.upsertByUsername(uname);
    await this.repo.touchLastLogin(user.id);
    return user;
  }
}
