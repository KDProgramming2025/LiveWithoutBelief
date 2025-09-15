import { buildUserRepository } from '../repositories/userRepository'

export class UserService {
  constructor(private readonly repo = buildUserRepository()) {}

  async summary(): Promise<{ total: number }> {
    const total = await this.repo.count()
    return { total }
  }

  async search(q: string, limit: number, offset: number) {
    const users = await this.repo.search(q, limit, offset)
    return { query: q, users }
  }

  async delete(id: string) {
    const res = await (this.repo as any).softDelete?.(id)
    if (!res) throw Object.assign(new Error('server_error'), { statusCode: 500 })
    if (res.deleted) return { ok: true }
    if (res.notFound) { const e: any = new Error('not_found'); e.statusCode = 404; throw e }
    return { ok: true }
  }
}
