import fs from 'node:fs/promises'
import fssync from 'node:fs'
import path from 'node:path'
import mammoth from 'mammoth'
import JSZip from 'jszip'
import { injectYouTubePlaceholders } from './YouTubeEmbedInjector.js'

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
    // Also produce a modified DOCX buffer which places markers where OLE objects occur
    const mediaResult = await this.extractMediaFromDocx(docxFinal, articleDir)

    // Inject YouTube placeholders (if any YouTube embeds present) working on either modifiedDocxBuffer or original docx file
    let workingBuffer: Buffer | undefined = mediaResult.modifiedDocxBuffer
    if (!workingBuffer) {
      workingBuffer = await fs.readFile(docxFinal)
    }
    const ytResult = await injectYouTubePlaceholders(workingBuffer)
    if (ytResult.modifiedBuffer) {
      workingBuffer = ytResult.modifiedBuffer
    }
    const extractedMedia = mediaResult.items

    // Convert docx → HTML using mammoth
    const mammothInput: any = workingBuffer ? { buffer: workingBuffer } : { path: docxFinal }
    const { value: initialHtml } = await mammoth.convertToHtml(mammothInput, {
      styleMap: [
        "p[style-name='Title'] => h1:fresh",
        "p[style-name='Subtitle'] => h2:fresh",
      ]
    })
    // Clean up: remove any OLE icon images that Mammoth rendered (usually EMF/WMF data URLs)
    // and remove icon-only blocks that appear immediately before our placeholders. Then
    // replace placeholders with media tags.
    let html = initialHtml
    // remove EMF/WMF images anywhere in the HTML
    html = removeOleIconDataImages(html)
    // Gather all placeholders (media + yt) for cleanup, then replace
    const mediaPlaceholders = mediaResult.inline.map(i => i.placeholder)
    const ytPlaceholders = (ytResult?.embeds || []).map(e => e.placeholder)
    const allPlaceholders = [...mediaPlaceholders, ...ytPlaceholders]
    if (allPlaceholders.length > 0) {
      html = removeOleIconBlocksBeforePlaceholders(html, allPlaceholders)
    }
    // Replace media placeholders
    for (const m of mediaResult.inline) {
      const tag = m.type === 'video'
        ? `<figure class=\"media__item\"><video controls src=\"./media/${m.filename}\"></video></figure>`
        : `<figure class=\"media__item\"><audio controls src=\"./media/${m.filename}\"></audio></figure>`
      const pattern = new RegExp(escapeRegExp(m.placeholder), 'g')
      html = html.replace(pattern, tag)
    }
    // Replace YouTube placeholders with iframe embeds
    if (ytResult && ytResult.embeds.length > 0) {
      for (const yt of ytResult.embeds) {
        const id = yt.videoId || yt.videoId || ''
        const iframe = id
          ? `<div class=\"media__item youtube\"><iframe src=\"https://www.youtube.com/embed/${id}\" title=\"YouTube video\" frameborder=\"0\" allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share\" allowfullscreen></iframe></div>`
          : `<div class=\"media__item youtube\"><a href=\"${yt.url}\">YouTube Video</a></div>`
        const pattern = new RegExp(escapeRegExp(yt.placeholder), 'g')
        html = html.replace(pattern, iframe)
      }
    }
    // Append extracted media players (if any)
    let bodyHtml = html
    const remaining = extractedMedia.filter(x => !mediaResult.inline.some(i => i.filename === x.filename))
    if (remaining.length > 0) {
      const items = remaining.map((m: ExtractedMedia) => {
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

  /**
   * Delete an article by id (or slug), removing:
   * - the associated docx file under this.docxDir
   * - the entire article public directory under this.webArticlesDir/<slug>
   * - the record from the JSON manifest
   * Returns true if a record was found and removed, false otherwise.
   */
  async delete(idOrSlug: string): Promise<boolean> {
    await this.ensure()
    const items = await this.list()
    const idx = items.findIndex(a => a.id === idOrSlug || a.slug === idOrSlug)
    if (idx < 0) return false
    const rec = items[idx]
    // Remove docx file if present
    try {
      if (rec.docxPath) await fs.rm(rec.docxPath, { force: true })
    } catch {}
    // Remove article directory recursively
    const articleDir = path.join(this.webArticlesDir, rec.slug)
    try {
      await fs.rm(articleDir, { recursive: true, force: true })
    } catch {}
    // Remove record and save
    items.splice(idx, 1)
    await this.saveAll(items)
    return true
  }

  // Extract embedded media (mp4/mp3) from DOCX into ./media
  // Returns: list of media items, inline placeholder mapping, and an optional modified docx buffer
  private async extractMediaFromDocx(docxPath: string, articleDir: string): Promise<{ items: ExtractedMedia[]; inline: Array<{ placeholder: string; filename: string; type: 'audio'|'video' }>; modifiedDocxBuffer?: Buffer }> {
    const items: ExtractedMedia[] = []
    const inline: Array<{ placeholder: string; filename: string; type: 'audio'|'video' }> = []
    const mediaDir = path.join(articleDir, 'media')
    await fs.mkdir(mediaDir, { recursive: true })
    const data = await fs.readFile(docxPath)
    const zip = await JSZip.loadAsync(data)

    // 1) Direct media under word/media
    const direct = Object.keys(zip.files).filter(p => p.startsWith('word/media/'))
    for (const p of direct) {
      const ext = path.extname(p).toLowerCase()
      const base = path.basename(p)
      const type = ext === '.mp4' ? 'video' : (ext === '.mp3' ? 'audio' : null)
      if (!type) continue
      const buf = await extractBufferFile(zip, p)
      await fs.writeFile(path.join(mediaDir, base), buf)
      items.push({ filename: base, type })
    }

    // 2) OLE-embedded media under word/embeddings/*.bin
    const embeddings = Object.keys(zip.files).filter(p => p.startsWith('word/embeddings/') && p.toLowerCase().endsWith('.bin'))
    const oleMap = new Map<string, { filename: string; type: 'audio'|'video' }>()
    for (const p of embeddings) {
      const buf = await extractBufferFile(zip, p)
      const sniff = sniffOleEmbedded(buf)
      if (sniff.start < 0 || !sniff.ext || !sniff.type) continue
      // derive output name from oleObject name
      const outName = path.basename(p, '.bin') + sniff.ext
      await fs.writeFile(path.join(mediaDir, outName), buf.subarray(sniff.start))
      items.push({ filename: outName, type: sniff.type })
      // store mapping with target like 'embeddings/oleObject1.bin'
      oleMap.set(p.replace(/^word\//, ''), { filename: outName, type: sniff.type })
    }

    // 3) If OLE entries exist, inject placeholders into word/document.xml next to their anchors
    let modifiedDocxBuffer: Buffer | undefined
    if (oleMap.size > 0) {
      const docXmlPath = 'word/document.xml'
      const relsPath = 'word/_rels/document.xml.rels'
      const docXml = await zip.file(docXmlPath)?.async('string')
      const relsXml = await zip.file(relsPath)?.async('string')
      if (docXml && relsXml) {
        // Map rId -> Target
        const relMap = new Map<string, string>()
        const relRe = /<Relationship[^>]*Id=\"([^\"]+)\"[^>]*Target=\"([^\"]+)\"[^>]*\/>/g
        let m: RegExpExecArray | null
        while ((m = relRe.exec(relsXml)) !== null) relMap.set(m[1], m[2])

        let newDocXml = docXml
        for (const [rId, target] of relMap.entries()) {
          const normTarget = target.replace(/^\.\//, '')
          if (!normTarget.startsWith('embeddings/')) continue
          const ole = oleMap.get(normTarget)
          if (!ole) continue
          const marker = `[[LWB_MEDIA:${normTarget}]]`
          // find usage(s) of r:id="rId"
          let from = 0
          while (true) {
            const idx = newDocXml.indexOf(`r:id=\"${rId}\"`, from)
            if (idx === -1) break
            const closeObj = newDocXml.indexOf('</w:object>', idx)
            const inject = `<w:p><w:r><w:t>${marker}</w:t></w:r></w:p>`
            if (closeObj !== -1) {
              const insertAt = closeObj + '</w:object>'.length
              newDocXml = newDocXml.slice(0, insertAt) + inject + newDocXml.slice(insertAt)
              from = insertAt + inject.length
              inline.push({ placeholder: marker, filename: ole.filename, type: ole.type })
            } else {
              const closePara = newDocXml.indexOf('</w:p>', idx)
              if (closePara !== -1) {
                const insertAt = closePara + '</w:p>'.length
                newDocXml = newDocXml.slice(0, insertAt) + inject + newDocXml.slice(insertAt)
                from = insertAt + inject.length
                inline.push({ placeholder: marker, filename: ole.filename, type: ole.type })
              } else {
                from = idx + 10
              }
            }
          }
        }
        // write back and repackage
        zip.file(docXmlPath, newDocXml)
        modifiedDocxBuffer = await zip.generateAsync({ type: 'nodebuffer' }) as unknown as Buffer
      }
    }

    return { items, inline, modifiedDocxBuffer }
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

// Escape string for literal usage in RegExp
function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// Remove images that are likely OLE icons embedded by Word (EMF/WMF rendered as data URLs)
function removeOleIconDataImages(html: string): string {
  let out = html
  // Strip <img src="data:image/x-emf;..."> and <img src="data:image/x-wmf;...">
  out = out.replace(/<img[^>]+src="data:image\/(x-emf|x-wmf)[^"]*"[^>]*>/gi, '')
  // If this made some empty blocks (<p> or <figure>) with only whitespace, remove them
  out = out.replace(/<(p|figure)([^>]*)>\s*<\/\1>/gi, '')
  return out
}

// Remove Word OLE icon image blocks that Mammoth renders before our injected placeholders.
// Heuristic: for each placeholder occurrence, if the immediately preceding block-level
// element (<p> or <figure>) contains an <img> and no meaningful text, drop that block.
function removeOleIconBlocksBeforePlaceholders(html: string, placeholders: string[]): string {
  let out = html
  for (const ph of placeholders) {
    let idx = out.indexOf(ph)
    while (idx !== -1) {
      // Find the nearest block end tag before the placeholder
      const prevPEnd = out.lastIndexOf('</p>', idx)
      const prevFigEnd = out.lastIndexOf('</figure>', idx)
      const prevHEnds: Array<{ tag: string; pos: number }> = []
      for (let level = 1; level <= 6; level++) {
        const end = out.lastIndexOf(`</h${level}>`, idx)
        if (end !== -1) prevHEnds.push({ tag: `h${level}`, pos: end })
      }
      let candidateEnd = prevPEnd
      let blockType: 'p' | 'figure' | 'heading' | null = null
      let headingTag: string | null = null
      if (prevFigEnd > candidateEnd) { candidateEnd = prevFigEnd; blockType = 'figure' }
      if (prevPEnd !== -1 && candidateEnd === prevPEnd) blockType = 'p'
      for (const h of prevHEnds) {
        if (h.pos > candidateEnd) { candidateEnd = h.pos; blockType = 'heading'; headingTag = h.tag }
      }
      if (candidateEnd === -1) break
      const endPos = candidateEnd
      // Find start tag based on block type
      let startTag: string
      if (blockType === 'p') startTag = '<p'
      else if (blockType === 'figure') startTag = '<figure'
      else if (blockType === 'heading' && headingTag) startTag = `<${headingTag}`
      else break
      const startPos = out.lastIndexOf(startTag, endPos)
      if (startPos === -1) break
      const closing = blockType === 'p' ? '</p>' : (blockType === 'figure' ? '</figure>' : (`</${headingTag}>`))
      const blockHtml = out.slice(startPos, endPos + closing.length)
      // Check if block is essentially an image-only container
      if (/<img\b/i.test(blockHtml)) {
        const withoutImg = blockHtml.replace(/<img[^>]*>/gi, '')
        const textOnly = withoutImg.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
        if (textOnly === '') {
          out = out.slice(0, startPos) + out.slice(endPos + closing.length)
          // After removal, recompute placeholder position from startPos
          idx = out.indexOf(ph, startPos)
          continue
        }
      }
      // Advance to next placeholder occurrence if nothing removed here
      idx = out.indexOf(ph, idx + ph.length)
    }
  }
  return out
}

// Sniff OLE .bin payload to find embedded media start offset and type/extension
function sniffOleEmbedded(buf: Buffer): { start: number; type?: 'audio'|'video'; ext?: string } {
  // Look for common tags
  const ftyp = buf.indexOf(Buffer.from('ftyp'))
  if (ftyp >= 4) {
    // assume MP4
    return { start: ftyp - 4, type: 'video', ext: '.mp4' }
  }
  const id3 = buf.indexOf(Buffer.from('ID3'))
  if (id3 >= 0) {
    return { start: id3, type: 'audio', ext: '.mp3' }
  }
  // RIFF (AVI/WAV), Ogg, etc. Not handled for now
  return { start: -1 }
}
