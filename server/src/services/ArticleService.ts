import fs from 'node:fs/promises'
import fssync from 'node:fs'
import path from 'node:path'
import mammoth from 'mammoth'

export interface ArticleRecord {
  id: string
  slug: string
  title: string
  label: string | null
  order: number
  coverUrl: string | null
  iconUrl: string | null
  docxPath: string
  indexUrl: string
  createdAt: string
  updatedAt: string
}

export class ArticleService {
  private baseDir = '/var/www/LWB/admin'
  private webArticlesDir = path.join(this.baseDir, 'web', 'articles')
  private docxDir = path.join(this.baseDir, 'docx')
  private jsonPath = path.join(this.baseDir, 'articles.json')

  constructor(private readonly baseUrl = 'https://aparat.feezor.net/LWB/Admin') {}

  async ensure(): Promise<void> {
    await fs.mkdir(this.webArticlesDir, { recursive: true })
    await fs.mkdir(this.docxDir, { recursive: true })
    if (!fssync.existsSync(this.jsonPath)) await fs.writeFile(this.jsonPath, '[]', 'utf8')
  }

  async list(): Promise<ArticleRecord[]> {
    await this.ensure()
    const raw = await fs.readFile(this.jsonPath, 'utf8')
    const arr = JSON.parse(raw) as ArticleRecord[]
    return arr.sort((a,b) => a.order - b.order || a.title.localeCompare(b.title))
  }

  private async saveAll(items: ArticleRecord[]) {
    await fs.writeFile(this.jsonPath, JSON.stringify(items, null, 2), 'utf8')
  }

  slugify(title: string): string {
    return title
      .toLowerCase()
      .replace(/[^a-z0-9\s-]/g, '')
      .trim()
      .replace(/\s+/g, '-')
  }

  async createOrReplace(input: { title: string; label?: string | null; order?: number; coverPath?: string | null; iconPath?: string | null; docxTmpPath: string; }): Promise<ArticleRecord> {
    await this.ensure()
    const list = await this.list()
    const slug = this.slugify(input.title)
    const articleDir = path.join(this.webArticlesDir, slug)
    await fs.mkdir(articleDir, { recursive: true })

    // Move docx to protected dir
    const docxFinal = path.join(this.docxDir, `${slug}${path.extname(input.docxTmpPath) || '.docx'}`)
    await fs.rename(input.docxTmpPath, docxFinal)

    // Existing record (if any) to preserve cover/icon when not supplied
    const existingIdx = list.findIndex(a => a.slug === slug)
    const existing = existingIdx >= 0 ? list[existingIdx] : null

    // Place cover/icon inside articleDir if provided (paths are temp uploads)
    let coverUrl: string | null = existing?.coverUrl ?? null
    if (input.coverPath) {
      const ext = path.extname(input.coverPath) || '.jpg'
      const dest = path.join(articleDir, `cover${ext}`)
      await fs.rename(input.coverPath, dest)
      coverUrl = `${this.baseUrl}/web/articles/${slug}/cover${ext}`
    }
    let iconUrl: string | null = existing?.iconUrl ?? null
    if (input.iconPath) {
      const ext = path.extname(input.iconPath) || '.png'
      const dest = path.join(articleDir, `icon${ext}`)
      await fs.rename(input.iconPath, dest)
      iconUrl = `${this.baseUrl}/web/articles/${slug}/icon${ext}`
    }

    // Convert docx → HTML using mammoth
    const { value: html } = await mammoth.convertToHtml({ path: docxFinal }, {
      styleMap: [
        "p[style-name='Title'] => h1:fresh",
        "p[style-name='Subtitle'] => h2:fresh",
      ]
    })
    const indexHtml = `<!doctype html><html><head><meta charset=\"utf-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/><title>${escapeHtml(input.title)}</title><link rel=\"stylesheet\" href=\"./styles.css\"/></head><body><main class=\"article\">${html}</main><script src=\"./script.js\" defer></script></body></html>`
    await fs.writeFile(path.join(articleDir, 'index.html'), indexHtml, 'utf8')
    await fs.writeFile(path.join(articleDir, 'styles.css'), defaultArticleCss, 'utf8')
    await fs.writeFile(path.join(articleDir, 'script.js'), defaultArticleJs, 'utf8')

    // Upsert into JSON list
    const now = new Date().toISOString()
    const rec: ArticleRecord = {
      id: existing?.id ?? String(Date.now()),
      slug,
      title: input.title,
      label: input.label ?? existing?.label ?? null,
      order: Number.isFinite(input.order) ? Number(input.order) : (existing ? existing.order : list.length),
      coverUrl,
      iconUrl,
      docxPath: docxFinal,
      indexUrl: `${this.baseUrl}/web/articles/${slug}/`,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    }
    if (existingIdx >= 0) list[existingIdx] = rec; else list.push(rec)
    await this.saveAll(list)
    return rec
  }
}

function escapeHtml(s: string){
  return s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[c] as string))
}

const defaultArticleCss = `
body{font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif;color:#0a0a0a;margin:0;padding:16px;background:#fff}
.article img{max-width:100%;height:auto}
.article h1,.article h2,.article h3{margin:18px 0 8px}
.article p{margin:12px 0}
`

const defaultArticleJs = `
// Custom article enhancements can go here
`
