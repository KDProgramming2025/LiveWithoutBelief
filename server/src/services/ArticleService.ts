import fs from 'node:fs/promises'
import fssync from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import mammoth from 'mammoth'
import JSZip from 'jszip'
import { injectYouTubePlaceholders } from './YouTubeEmbedInjector.js'
import { env } from '../server/config/env.js'

export interface ArticleRecord {
  id: string
  slug: string
  title: string
  label: string | null
  order: number
  coverUrl: string | null
  iconUrl: string | null
  docxPath: string
  indexUrl: string | null
  createdAt: string
  updatedAt: string
}

export class ArticleService {
  private baseDir = '/var/www/LWB/admin'
  private webArticlesDir = path.join(this.baseDir, 'web', 'articles')
  private docxDir = path.join(this.baseDir, 'docx')
  private jsonPath = path.join(this.baseDir, 'articles.json')
  // Template resolution: prefer dist/templates when compiled, fallback to src/templates during dev
  private readonly templateResolver = createTemplateResolver(import.meta.url)

  constructor(private readonly baseUrl: string | null = (env.APP_SERVER_HOST ? `${env.APP_SERVER_SCHEME}://${env.APP_SERVER_HOST}/LWB/Admin` : null)) { }

  async ensure(): Promise<void> {
    await fs.mkdir(this.webArticlesDir, { recursive: true })
    await fs.mkdir(this.docxDir, { recursive: true })
    if (!fssync.existsSync(this.jsonPath)) await fs.writeFile(this.jsonPath, '[]', 'utf8')
  }

  async list(): Promise<ArticleRecord[]> {
    await this.ensure()
    const raw = await fs.readFile(this.jsonPath, 'utf8')
    const arr = JSON.parse(raw) as ArticleRecord[]
    return arr.sort((a, b) => a.order - b.order || a.title.localeCompare(b.title))
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
      coverUrl = this.baseUrl ? `${this.baseUrl}/web/articles/${slug}/cover${ext}` : null
    }
    let iconUrl: string | null = existing?.iconUrl ?? null
    if (input.iconPath) {
      const ext = path.extname(input.iconPath) || '.png'
      const dest = path.join(articleDir, `icon${ext}`)
      await fs.rename(input.iconPath, dest)
      iconUrl = this.baseUrl ? `${this.baseUrl}/web/articles/${slug}/icon${ext}` : null
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
  // mediaResult.items is tracked via inline placeholders; no separate variable required here

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
    // Load snippet templates
    const [videoItemTpl, audioItemTpl, imageItemTpl, ytEmbedTpl, ytLinkTpl] = await Promise.all([
      this.templateResolver('articles', 'media-item-video.html'),
      this.templateResolver('articles', 'media-item-audio.html'),
      this.templateResolver('articles', 'media-item-image.html'),
      this.templateResolver('articles', 'youtube-embed.html'),
      this.templateResolver('articles', 'youtube-link.html'),
    ])
    // Replace media placeholders via templates
    for (const m of mediaResult.inline) {
      const src = `./media/${m.filename}`
      const tag = m.type === 'video'
        ? renderTpl(videoItemTpl, { SRC: src })
        : renderTpl(audioItemTpl, { SRC: src })
      const pattern = new RegExp(escapeRegExp(m.placeholder), 'g')
      html = html.replace(pattern, tag)
    }
    // Note: image wrapping is performed AFTER YouTube replacement/cleanup to avoid wrapping YT thumbnails.
    // Replace YouTube placeholders with iframe embeds
    if (ytResult && ytResult.embeds.length > 0) {
      for (const yt of ytResult.embeds) {
        const id = yt.videoId || yt.videoId || ''
        const iframe = id
          ? renderTpl(ytEmbedTpl, { VIDEO_ID: id })
          : renderTpl(ytLinkTpl, { URL: yt.url ?? '' })
        const pattern = new RegExp(escapeRegExp(yt.placeholder), 'g')
        html = html.replace(pattern, iframe)
      }
      // Robust cleanup: remove any base64 thumbnail <img> inside a heading that also contains a YouTube embed.
      // We perform iterative passes to catch variants like:
      // <h2><img ... /><div class="media__item youtube">...</div></h2>
      // <h2><img ... /><p><div class="media__item youtube">...</div></p></h2>
      // <h2><img ... /><p><span><div class="media__item youtube">...</div></span></p></h2>
      // Strategy:
      // 1. Find each heading block.
      // 2. If it contains a youtube embed div.
      // 3. Remove all <img src="data:image/*;base64,..."> inside that heading.
      // 4. Collapse trivial wrappers (<p>, <span>, <strong>, <em>) that only wrap the youtube div.
      // 5. If heading text content becomes empty (only the youtube div remains), unwrap the heading to a neutral div wrapper.
      const headingRe = /<h([1-6])([^>]*)>([\s\S]*?)<\/h\1>/gi
      html = html.replace(headingRe, (
        full: string,
        level: string,
        attrs: string,
        inner: string
      ) => {
        if (!/class="media__item youtube"/i.test(inner)) return full
        // Remove base64 images
        let updated = inner.replace(/<img[^>]+src="data:image\/[a-zA-Z0-9+]+;base64,[^"]+"[^>]*>/gi, '')
        // Remove empty paragraphs/spans created by image removal
        updated = updated.replace(/<(p|span|strong|em)[^>]*>\s*<\/\1>/gi, '')
        // If youtube div wrapped inside trivial single wrapper(s), unwrap them
        // Repeat a few times to collapse nesting
        for (let i = 0; i < 5; i++) {
          updated = updated.replace(/^(?:\s*<(p|span|strong|em)[^>]*>\s*)+(<div class="media__item youtube">[\s\S]*?<\/div>)(?:\s*<\/(?:p|span|strong|em)>\s*)+$/i, '$2')
          // Also unwrap when extra <p> ... </p> only contains whitespace + youtube div
          updated = updated.replace(/^\s*<p[^>]*>\s*(<div class="media__item youtube">[\s\S]*?<\/div>)\s*<\/p>\s*$/i, '$1')
        }
        const textContent = updated
          .replace(/<div class="media__item youtube">[\s\S]*?<\/div>/gi, '')
          .replace(/<[^>]+>/g, '')
          .replace(/&nbsp;/g, ' ')
          .trim()
        // If no remaining textual content besides the video, drop the heading semantics
        if (textContent === '') {
          return `<div class="youtube-heading-wrap h${level}">${updated}</div>`
        }
        return `<h${level}${attrs}>${updated}</h${level}>`
      })
    }
    // Now wrap remaining plain <img ...> tags with the image template (adds the question button),
    // avoiding double-wrap if already inside a figure.media__item
  html = html.replace(/<img\b([^>]*?)src="([^"]+)"([^>]*)>/gi,
      (full: string, pre: string, src: string, post: string, offset: number, whole: string): string => {
        const lookBehindStart = Math.max(0, offset - 800)
        const lookAheadEnd = Math.min(whole.length, offset + full.length + 800)
        const before = whole.slice(lookBehindStart, offset)
        const after = whole.slice(offset + full.length, lookAheadEnd)
        const figureOpenIdx = before.lastIndexOf('<figure')
  const hasMediaClass = figureOpenIdx >= 0 && /<figure[^>]*class="[^"]*\bmedia__item\b[^"]*"/i.test(before.slice(figureOpenIdx))
  const figureCloseAfter = new RegExp('</figure>', 'i').test(after)
        if (hasMediaClass && figureCloseAfter) return full
        return renderTpl(imageItemTpl, { SRC: src, ALT: extractAltFromImg(full) })
      })
    // No non-inline media append; only inline replacements are supported.
    const [htmlTpl, cssTpl, jsTpl] = await Promise.all([
      this.templateResolver('articles', 'article.html'),
      this.templateResolver('articles', 'styles.css'),
      this.templateResolver('articles', 'script.js'),
    ])
    const assetsVersion = String(Date.now())
    const indexHtml = htmlTpl
      .replace(/\{\{\s*TITLE\s*\}\}/g, escapeHtml(input.title))
      .replace(/\{\{\s*BODY\s*\}\}/g, html)
      .replace(/href="\.\/styles\.css"/g, `href="./styles.css?v=${assetsVersion}"`)
      .replace(/src="\.\/script\.js"/g, `src="./script.js?v=${assetsVersion}"`)
    await fs.writeFile(path.join(articleDir, 'index.html'), indexHtml, 'utf8')
    await fs.writeFile(path.join(articleDir, 'styles.css'), cssTpl, 'utf8')
    await fs.writeFile(path.join(articleDir, 'script.js'), jsTpl, 'utf8')

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
      indexUrl: this.baseUrl ? `${this.baseUrl}/web/articles/${slug}/` : null,
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
    } catch { void 0 }
    // Remove article directory recursively
    const articleDir = path.join(this.webArticlesDir, rec.slug)
    try {
      await fs.rm(articleDir, { recursive: true, force: true })
    } catch { void 0 }
    // Remove record and save
    items.splice(idx, 1)
    await this.saveAll(items)
    return true
  }

  /**
   * Move an article up or down by swapping its order with the closest neighbor.
   * Accepts id or slug.
   */
  async move(idOrSlug: string, direction: 'up' | 'down'): Promise<boolean> {
    await this.ensure()
    const items = await this.list()
    const idx = items.findIndex(a => a.id === idOrSlug || a.slug === idOrSlug)
    if (idx < 0) return false
    // Determine neighbor based on desired direction and ordering rules
    // The list() result is already sorted by order then title, but we'll compute neighbors by order fields.
    const current = items[idx]
    // Find candidate neighbor indices among all items
    let neighborIndex = -1
    if (direction === 'up') {
      // Prefer previous item with <= order (closest above)
      for (let i = idx - 1; i >= 0; i--) { neighborIndex = i; break }
    } else {
      for (let i = idx + 1; i < items.length; i++) { neighborIndex = i; break }
    }
    if (neighborIndex < 0) return false
    const neighbor = items[neighborIndex]
    // Swap order values
    const aOrder = Number.isFinite(current.order) ? current.order : 0
    const bOrder = Number.isFinite(neighbor.order) ? neighbor.order : 0
    const newItems = items.slice()
    newItems[idx] = { ...current, order: bOrder, updatedAt: new Date().toISOString() }
    newItems[neighborIndex] = { ...neighbor, order: aOrder, updatedAt: new Date().toISOString() }
    // Persist exactly as stored (unsorted), then rely on list() to sort on read
    await this.saveAll(newItems)
    return true
  }

  /**
   * Partial update for an article. Only provided fields are applied.
   * Supports: title (may change slug and move folder/docx), label, order,
   * coverPath/iconPath replacements, and docxTmpPath reprocessing.
   */
  async update(idOrSlug: string, input: { title?: string; label?: string | null; order?: number; coverPath?: string; iconPath?: string; docxTmpPath?: string }): Promise<ArticleRecord | null> {
    await this.ensure()
    // Load current records unsorted; find by id or slug
    const raw = await fs.readFile(this.jsonPath, 'utf8')
    const items = JSON.parse(raw) as ArticleRecord[]
    const idx = items.findIndex(a => a.id === idOrSlug || a.slug === idOrSlug)
    if (idx < 0) return null
    const rec = items[idx]
    let slug = rec.slug
    const now = new Date().toISOString()

    // Handle title change (may require slug change and moving folders/files)
    if (typeof input.title === 'string' && input.title.trim() !== '' && input.title !== rec.title) {
      const newSlug = this.slugify(input.title)
      if (newSlug !== rec.slug) {
        // Move articleDir and docx file
        const oldDir = path.join(this.webArticlesDir, rec.slug)
        const newDir = path.join(this.webArticlesDir, newSlug)
        try { await fs.mkdir(newDir, { recursive: true }) } catch { void 0 }
        try { await fs.rename(oldDir, newDir) } catch { void 0 }
        // Move docx path to new slug filename (keep extension)
        try {
          const ext = path.extname(rec.docxPath) || '.docx'
          const newDocx = path.join(this.docxDir, `${newSlug}${ext}`)
          await fs.rename(rec.docxPath, newDocx)
          rec.docxPath = newDocx
        } catch { void 0 }
        slug = newSlug
      }
      rec.title = input.title
    }

    // Optional label/order
    if (input.label !== undefined) rec.label = input.label
    if (Number.isFinite(input.order)) rec.order = Number(input.order)

    // Replace cover/icon if provided (place into articleDir)
    const articleDir = path.join(this.webArticlesDir, slug)
    if (input.coverPath) {
      const ext = path.extname(input.coverPath) || '.jpg'
      const dest = path.join(articleDir, `cover${ext}`)
      await fs.rename(input.coverPath, dest)
      rec.coverUrl = this.baseUrl ? `${this.baseUrl}/web/articles/${slug}/cover${ext}` : null
    }
    if (input.iconPath) {
      const ext = path.extname(input.iconPath) || '.png'
      const dest = path.join(articleDir, `icon${ext}`)
      await fs.rename(input.iconPath, dest)
      rec.iconUrl = this.baseUrl ? `${this.baseUrl}/web/articles/${slug}/icon${ext}` : null
    }

    // Reprocess DOCX if a new one was provided
    if (input.docxTmpPath) {
      // Move temp docx to protected dir with current slug
      const ext = path.extname(input.docxTmpPath) || '.docx'
      const docxFinal = path.join(this.docxDir, `${slug}${ext}`)
      await fs.rename(input.docxTmpPath, docxFinal)
      rec.docxPath = docxFinal
      // Re-run media extraction and HTML generation similar to createOrReplace
      const mediaResult = await this.extractMediaFromDocx(docxFinal, articleDir)
      let workingBuffer: Buffer | undefined = mediaResult.modifiedDocxBuffer
      if (!workingBuffer) { workingBuffer = await fs.readFile(docxFinal) }
      const ytResult = await injectYouTubePlaceholders(workingBuffer)
      if (ytResult.modifiedBuffer) workingBuffer = ytResult.modifiedBuffer
      const mammothInput: any = workingBuffer ? { buffer: workingBuffer } : { path: docxFinal }
      const { value: initialHtml } = await mammoth.convertToHtml(mammothInput, {
        styleMap: [
          "p[style-name='Title'] => h1:fresh",
          "p[style-name='Subtitle'] => h2:fresh",
        ]
      })
      let html = removeOleIconDataImages(initialHtml)
      const mediaPlaceholders = mediaResult.inline.map(i => i.placeholder)
      const ytPlaceholders = (ytResult?.embeds || []).map(e => e.placeholder)
      const allPlaceholders = [...mediaPlaceholders, ...ytPlaceholders]
      if (allPlaceholders.length > 0) {
        html = removeOleIconBlocksBeforePlaceholders(html, allPlaceholders)
      }
      const [videoItemTpl, audioItemTpl, imageItemTpl, ytEmbedTpl, ytLinkTpl] = await Promise.all([
        this.templateResolver('articles', 'media-item-video.html'),
        this.templateResolver('articles', 'media-item-audio.html'),
        this.templateResolver('articles', 'media-item-image.html'),
        this.templateResolver('articles', 'youtube-embed.html'),
        this.templateResolver('articles', 'youtube-link.html'),
      ])
      for (const m of mediaResult.inline) {
        const src = `./media/${m.filename}`
        const tag = m.type === 'video' ? renderTpl(videoItemTpl, { SRC: src }) : renderTpl(audioItemTpl, { SRC: src })
        const pattern = new RegExp(escapeRegExp(m.placeholder), 'g')
        html = html.replace(pattern, tag)
      }
      // Wrap plain <img> tags with the image template; avoid double-wrapping if already inside our media figure
      html = html.replace(/<img\b([^>]*?)src="([^"]+)"([^>]*)>/gi,
        (full: string, pre: string, src: string, post: string, offset: number, whole: string): string => {
          // Wrap data URI images as well for consistency
          const lookBehindStart = Math.max(0, offset - 800)
          const lookAheadEnd = Math.min(whole.length, offset + full.length + 800)
          const before = whole.slice(lookBehindStart, offset)
          const after = whole.slice(offset + full.length, lookAheadEnd)
          const figureOpenIdx = before.lastIndexOf('<figure')
          const hasMediaClass = figureOpenIdx >= 0 && /<figure[^>]*class="[^"]*\bmedia__item\b[^"]*"/i.test(before.slice(figureOpenIdx))
          const figureCloseAfter = /<\/figure>/i.test(after)
          if (hasMediaClass && figureCloseAfter) return full
          return renderTpl(imageItemTpl, { SRC: src, ALT: extractAltFromImg(full) })
        })
      if (ytResult && ytResult.embeds.length > 0) {
        for (const yt of ytResult.embeds) {
          const id = yt.videoId || yt.videoId || ''
          const iframe = id ? renderTpl(ytEmbedTpl, { VIDEO_ID: id }) : renderTpl(ytLinkTpl, { URL: yt.url ?? '' })
          const pattern = new RegExp(escapeRegExp(yt.placeholder), 'g')
          html = html.replace(pattern, iframe)
        }
        // Mirror robust cleanup from createOrReplace: remove base64 thumbnail <img> inside a heading containing a YouTube embed
        const headingRe = /<h([1-6])([^>]*)>([\s\S]*?)<\/h\1>/gi
        html = html.replace(headingRe, (
          full: string,
          level: string,
          attrs: string,
          inner: string
        ) => {
          if (!/class="media__item youtube"/i.test(inner)) return full
          let updated = inner.replace(/<img[^>]+src="data:image\/[a-zA-Z0-9+]+;base64,[^"]+"[^>]*>/gi, '')
          updated = updated.replace(/<(p|span|strong|em)[^>]*>\s*<\/(?:p|span|strong|em)>/gi, '')
          for (let i = 0; i < 5; i++) {
            updated = updated.replace(/^(?:\s*<(p|span|strong|em)[^>]*>\s*)+(<div class="media__item youtube">[\s\S]*?<\/div>)(?:\s*<\/(?:p|span|strong|em)>\s*)+$/i, '$2')
            updated = updated.replace(/^\s*<p[^>]*>\s*(<div class="media__item youtube">[\s\S]*?<\/div>)\s*<\/p>\s*$/i, '$1')
          }
          const textContent = updated
            .replace(/<div class="media__item youtube">[\s\S]*?<\/div>/gi, '')
            .replace(/<[^>]+>/g, '')
            .replace(/&nbsp;/g, ' ')
            .trim()
          if (textContent === '') {
            return `<div class="youtube-heading-wrap h${level}">${updated}</div>`
          }
          return `<h${level}${attrs}>${updated}</h${level}>`
        })
      }
      // After YT cleanup, wrap remaining images with the image template and avoid double-wrap
      html = html.replace(/<img\b([^>]*?)src="([^"]+)"([^>]*)>/gi,
        (full: string, pre: string, src: string, post: string, offset: number, whole: string): string => {
          const lookBehindStart = Math.max(0, offset - 800)
          const lookAheadEnd = Math.min(whole.length, offset + full.length + 800)
          const before = whole.slice(lookBehindStart, offset)
          const after = whole.slice(offset + full.length, lookAheadEnd)
          const figureOpenIdx = before.lastIndexOf('<figure')
          const hasMediaClass = figureOpenIdx >= 0 && /<figure[^>]*class="[^"]*\bmedia__item\b[^"]*"/i.test(before.slice(figureOpenIdx))
          const figureCloseAfter = new RegExp('</figure>', 'i').test(after)
          if (hasMediaClass && figureCloseAfter) return full
          return renderTpl(imageItemTpl, { SRC: src, ALT: extractAltFromImg(full) })
        })
      // No non-inline media append; only inline replacements are supported.
      const [htmlTpl, cssTpl, jsTpl] = await Promise.all([
        this.templateResolver('articles', 'article.html'),
        this.templateResolver('articles', 'styles.css'),
        this.templateResolver('articles', 'script.js'),
      ])
      const assetsVersion = String(Date.now())
      const indexHtml = htmlTpl
        .replace(/\{\{\s*TITLE\s*\}\}/g, escapeHtml(rec.title))
        .replace(/\{\{\s*BODY\s*\}\}/g, html)
        .replace(/href="\.\/styles\.css"/g, `href="./styles.css?v=${assetsVersion}"`)
        .replace(/src="\.\/script\.js"/g, `src="./script.js?v=${assetsVersion}"`)
      await fs.writeFile(path.join(articleDir, 'index.html'), indexHtml, 'utf8')
      await fs.writeFile(path.join(articleDir, 'styles.css'), cssTpl, 'utf8')
      await fs.writeFile(path.join(articleDir, 'script.js'), jsTpl, 'utf8')
    }

    // Update slug-related fields if changed
    rec.slug = slug
    rec.indexUrl = this.baseUrl ? `${this.baseUrl}/web/articles/${slug}/` : null
    rec.updatedAt = now
    items[idx] = rec
    await this.saveAll(items)
    return rec
  }

  // Extract embedded media (mp4/mp3) from DOCX into ./media
  // Returns: list of media items, inline placeholder mapping, and an optional modified docx buffer
  private async extractMediaFromDocx(docxPath: string, articleDir: string): Promise<{ items: ExtractedMedia[]; inline: Array<{ placeholder: string; filename: string; type: 'audio' | 'video' }>; modifiedDocxBuffer?: Buffer }> {
    const items: ExtractedMedia[] = []
    const inline: Array<{ placeholder: string; filename: string; type: 'audio' | 'video' }> = []
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
    const oleMap = new Map<string, { filename: string; type: 'audio' | 'video' }>()
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
        const relRe = /<Relationship[^>]*Id="([^"]+)"[^>]*Target="([^"]+)"[^>]*\/>/g
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
          let idx = -1
          while ((idx = newDocXml.indexOf(`r:id="${rId}"`, from)) !== -1) {
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

function escapeHtml(s: string) {
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', '\'': '&#39;' }[c] as string))
}

// Extract alt attribute from an <img ...> tag markup (very lightweight; no full HTML parse)
function extractAltFromImg(tag: string): string {
  const m = tag.match(/\balt="([^"]*)"/i)
  if (!m) return ''
  return escapeHtml(m[1])
}

// Template resolver to load static HTML/CSS/JS from files instead of inline strings
function createTemplateResolver(metaUrl: string) {
  const __filename = fileURLToPath(metaUrl)
  const __dirname = path.dirname(__filename)
  // Candidates: dist/templates, src/templates, repo-root/server/src/templates
  const candidates = (
    subdir: string,
    file: string
  ) => [
      path.resolve(__dirname, `../templates/${subdir}/${file}`), // dist/services -> dist/templates
      path.resolve(__dirname, `../../src/templates/${subdir}/${file}`), // dist/services -> src/templates
      path.resolve(process.cwd(), `server/src/templates/${subdir}/${file}`), // fallback when cwd is repo root
    ]
  return async (subdir: string, file: string): Promise<string> => {
    for (const p of candidates(subdir, file)) {
      try {
        if (fssync.existsSync(p)) return await fs.readFile(p, 'utf8')
      } catch { /* try next */ }
    }
    throw new Error(`Template not found: ${subdir}/${file}`)
  }
}

// Very small template renderer for {{ KEY }} tokens
function renderTpl(tpl: string, data: Record<string, string>): string {
  let out = tpl
  for (const [k, v] of Object.entries(data)) {
    const re = new RegExp(`\\{\\{\\s*${escapeRegExp(k)}\\s*\\}\\}`, 'g')
    out = out.replace(re, v)
  }
  return out
}

export type ExtractedMedia = { filename: string; type: 'audio' | 'video' }

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
function sniffOleEmbedded(buf: Buffer): { start: number; type?: 'audio' | 'video'; ext?: string } {
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
