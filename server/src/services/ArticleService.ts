import fs from 'node:fs/promises'
import fssync from 'node:fs'
import path from 'node:path'
import mammoth from 'mammoth'
import JSZip from 'jszip'

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

    // Extract embedded media from DOCX (mp4/mp3) into ./media
  const extractedMedia = await this.extractMediaFromDocx(docxFinal, articleDir)

    // Convert docx → HTML using mammoth
    const { value: html } = await mammoth.convertToHtml({ path: docxFinal }, {
      styleMap: [
        "p[style-name='Title'] => h1:fresh",
        "p[style-name='Subtitle'] => h2:fresh",
      ]
    })
    // Append extracted media players (if any)
    let bodyHtml = html
    if (extractedMedia.length > 0) {
      const items = extractedMedia.map((m: ExtractedMedia) => {
        const src = `./media/${m.filename}`
        return m.type === 'video'
          ? `<figure class=\"media__item\"><video controls src=\"${src}\"></video></figure>`
          : `<figure class=\"media__item\"><audio controls src=\"${src}\"></audio></figure>`
      }).join('')
      bodyHtml += `<section class=\"media\"><h2>Media</h2>${items}</section>`
    }
    const indexHtml = `<!doctype html><html><head><meta charset=\"utf-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/><title>${escapeHtml(input.title)}</title><link rel=\"stylesheet\" href=\"./styles.css\"/></head><body><main class=\"article\">${bodyHtml}</main><script src=\"./script.js\" defer></script></body></html>`
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

  // Extract embedded media (mp4/mp3) from DOCX into ./media
  private async extractMediaFromDocx(docxPath: string, articleDir: string): Promise<ExtractedMedia[]> {
    const out: ExtractedMedia[] = []
    const mediaDir = path.join(articleDir, 'media')
    await fs.mkdir(mediaDir, { recursive: true })
    const data = await fs.readFile(docxPath)
    const zip = await JSZip.loadAsync(data)
    const candidates = Object.keys(zip.files).filter(p => p.startsWith('word/media/'))
    for (const p of candidates) {
      const ext = path.extname(p).toLowerCase()
      const base = path.basename(p)
      const type = ext === '.mp4' ? 'video' : (ext === '.mp3' ? 'audio' : null)
      if (!type) continue
      const buf = await extractBufferFile(zip, p)
      await fs.writeFile(path.join(mediaDir, base), buf)
      out.push({ filename: base, type })
    }
    return out
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
.media{margin-top:24px;padding-top:8px;border-top:1px solid #ddd}
.media h2{font-size:18px;margin:0 0 12px}
.media__item{margin:8px 0}
video{max-width:100%;height:auto;background:#000}
audio{width:100%}
`

const defaultArticleJs = `
// Enhance YouTube links into embeds
document.addEventListener('DOMContentLoaded', () => {
  const anchors = Array.from(document.querySelectorAll('a[href]'))
  for(const a of anchors){
    const href = a.getAttribute('href') || ''
    const vid = extractYouTubeId(href)
    if(!vid) continue
    const iframe = document.createElement('iframe')
    iframe.width = '560'; iframe.height = '315'
    iframe.src = 'https://www.youtube.com/embed/' + vid
    iframe.title = 'YouTube video player'
    iframe.frameBorder = '0'
    iframe.allow = 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share'
    iframe.allowFullscreen = true
    const wrap = document.createElement('div')
    wrap.style.position = 'relative'; wrap.style.paddingBottom = '56.25%'; wrap.style.height = '0'; wrap.style.margin = '12px 0'
    iframe.style.position = 'absolute'; iframe.style.top = '0'; iframe.style.left = '0'; iframe.style.width = '100%'; iframe.style.height = '100%'
    wrap.appendChild(iframe)
    a.insertAdjacentElement('afterend', wrap)
  }
  function extractYouTubeId(u){
    try{
      const url = new URL(u)
      if(url.hostname.includes('youtube.com')) return url.searchParams.get('v')
      if(url.hostname.includes('youtu.be')) return url.pathname.replace(/^\//,'')
    }catch{}
    return null
  }
})
`

export type ExtractedMedia = { filename: string; type: 'audio'|'video' }

// Helper to read file contents from docx zip
async function extractBufferFile(zip: JSZip, pathInZip: string): Promise<Buffer> {
  const file = zip.file(pathInZip)
  if (!file) throw new Error('missing ' + pathInZip)
  const buf = await file.async('nodebuffer')
  return buf as unknown as Buffer
}

// (no more duplicate class)
