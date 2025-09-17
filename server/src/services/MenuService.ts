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
}
