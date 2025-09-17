type PgPool = { query: (text: string, params?: any[]) => Promise<any> }

export interface MenuItem {
  id: string
  title: string
  label: string | null
  order: number
  iconPath: string | null
  createdAt: string
}

export class MenuService {
  private ensured = false
  constructor(private readonly pool: PgPool) {}

  private async ensure(): Promise<void> {
    if (this.ensured) return
    await this.pool.query(`
      CREATE TABLE IF NOT EXISTS menu_items (
        id BIGSERIAL PRIMARY KEY,
        title TEXT NOT NULL,
        label TEXT,
        ord INT NOT NULL DEFAULT 0,
        icon_path TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
    `)
    this.ensured = true
  }

  async list(): Promise<MenuItem[]> {
    await this.ensure()
    const r = await this.pool.query(
      'SELECT id, title, label, ord, icon_path, created_at FROM menu_items ORDER BY ord ASC, id ASC'
    )
    return r.rows.map((row: any) => ({
      id: String(row.id),
      title: row.title,
      label: row.label ?? null,
      order: Number(row.ord ?? 0),
      iconPath: row.icon_path ?? null,
      createdAt: row.created_at ? new Date(row.created_at).toISOString() : ''
    }))
  }

  async create(input: { title: string; label?: string | null; order?: number; iconPath?: string | null }): Promise<MenuItem> {
    await this.ensure()
    const ord = Number.isFinite(input.order) ? Number(input.order) : 0
    const r = await this.pool.query(
      `INSERT INTO menu_items (title, label, ord, icon_path)
       VALUES ($1, $2, $3, $4)
       RETURNING id, title, label, ord, icon_path, created_at`,
      [input.title, input.label ?? null, ord, input.iconPath ?? null]
    )
    const row = r.rows[0]
    return {
      id: String(row.id),
      title: row.title,
      label: row.label ?? null,
      order: Number(row.ord ?? 0),
      iconPath: row.icon_path ?? null,
      createdAt: row.created_at ? new Date(row.created_at).toISOString() : ''
    }
  }

  async delete(id: string): Promise<boolean> {
    await this.ensure()
    const r = await this.pool.query('DELETE FROM menu_items WHERE id = $1', [id])
    return r.rowCount > 0
  }

  async update(id: string, input: { title?: string; label?: string | null; order?: number }): Promise<MenuItem | null> {
    await this.ensure()
    const fields: string[] = []
    const values: any[] = []
    let i = 1
    if (typeof input.title === 'string') { fields.push(`title = $${i++}`); values.push(input.title) }
    if (input.label !== undefined) { fields.push(`label = $${i++}`); values.push(input.label) }
    if (Number.isFinite(input.order)) { fields.push(`ord = $${i++}`); values.push(Number(input.order)) }
    if (!fields.length) return this.getById(id)
    values.push(id)
    const r = await this.pool.query(
      `UPDATE menu_items SET ${fields.join(', ')} WHERE id = $${i} RETURNING id, title, label, ord, icon_path, created_at`,
      values
    )
    if (!r.rows[0]) return null
    const row = r.rows[0]
    return {
      id: String(row.id),
      title: row.title,
      label: row.label ?? null,
      order: Number(row.ord ?? 0),
      iconPath: row.icon_path ?? null,
      createdAt: row.created_at ? new Date(row.created_at).toISOString() : ''
    }
  }

  async updateIcon(id: string, iconPath: string | null): Promise<boolean> {
    await this.ensure()
    const r = await this.pool.query('UPDATE menu_items SET icon_path = $1 WHERE id = $2', [iconPath, id])
    return r.rowCount > 0
  }

  private async getById(id: string): Promise<MenuItem | null> {
    await this.ensure()
    const r = await this.pool.query('SELECT id, title, label, ord, icon_path, created_at FROM menu_items WHERE id = $1', [id])
    const row = r.rows[0]
    if (!row) return null
    return {
      id: String(row.id),
      title: row.title,
      label: row.label ?? null,
      order: Number(row.ord ?? 0),
      iconPath: row.icon_path ?? null,
      createdAt: row.created_at ? new Date(row.created_at).toISOString() : ''
    }
  }

  async move(id: string, direction: 'up' | 'down'): Promise<boolean> {
    await this.ensure()
    const cur = await this.pool.query('SELECT id, ord FROM menu_items WHERE id = $1', [id])
    const row = cur.rows[0]
    if (!row) return false
    const ord = Number(row.ord ?? 0)
    const neighbor = await this.pool.query(
      direction === 'up'
        ? 'SELECT id, ord FROM menu_items WHERE (ord < $1) OR (ord = $1 AND id < $2) ORDER BY ord DESC, id DESC LIMIT 1'
        : 'SELECT id, ord FROM menu_items WHERE (ord > $1) OR (ord = $1 AND id > $2) ORDER BY ord ASC, id ASC LIMIT 1',
      [ord, row.id]
    )
    const nb = neighbor.rows[0]
    if (!nb) return false
    const nbOrd = Number(nb.ord ?? 0)
    try {
      await this.pool.query('BEGIN')
      await this.pool.query('UPDATE menu_items SET ord = $1 WHERE id = $2', [nbOrd, row.id])
      await this.pool.query('UPDATE menu_items SET ord = $1 WHERE id = $2', [ord, nb.id])
      await this.pool.query('COMMIT')
      return true
    } catch (e) {
      await this.pool.query('ROLLBACK')
      throw e
    }
  }
}
