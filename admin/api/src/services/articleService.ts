import path from 'path'
import fs from 'fs/promises'
import os from 'os'
import sanitizeHtml from 'sanitize-html'
import mammothDefault from 'mammoth'
import type { ArticleMeta } from '../types'
import { CONFIG } from '../config'
import { writeFileAtomic, ensureDirSync } from '../utils/fsx'
import { getImageExtFromNameOrMime } from '../utils/images'
import { slugify } from '../utils/strings'
import { buildArticleRepository } from '../repositories/articleRepository'

const MAMMOTH = mammothDefault as unknown as {
  images: { inline: (handler: any) => any }
  convertToHtml: (input: { path: string }, options?: { convertImage?: any }) => Promise<{ value: string }>
}

export class ArticleService {
  constructor(private readonly repo = buildArticleRepository()) {}

  async list(): Promise<ArticleMeta[]> {
    return this.repo.readMeta()
  }

  async move(id: string, direction: 'up'|'down'): Promise<void> {
    const items = await this.repo.readMeta()
    const idx = items.findIndex(i => i.id === id)
    if (idx < 0) throw Object.assign(new Error('not_found'), { statusCode: 404 })
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1
    if (swapIdx < 0 || swapIdx >= items.length) throw Object.assign(new Error('out_of_bounds'), { statusCode: 400 })
    const tmpOrder = items[idx].order
    items[idx].order = items[swapIdx].order
    items[swapIdx].order = tmpOrder
    const [a] = items.splice(idx, 1)
    items.splice(swapIdx, 0, a)
    await this.repo.writeMeta(items)
  }

  async edit(id: string, params: { title?: string; files?: Array<any>; logger?: any }): Promise<{ item: ArticleMeta; warnings: Array<{ field: 'cover'|'icon'; error: string }> }> {
    const items = await this.repo.readMeta()
    const idx = items.findIndex(i => i.id === id)
    if (idx < 0) throw Object.assign(new Error('not_found'), { statusCode: 404 })
    const art = items[idx]

    const warnings: Array<{ field: 'cover'|'icon'; error: string }> = []
    let titleChanged = false
    let coverWritten = false
    let iconWritten = false
    const newTitle = params.title?.trim()
    if (newTitle && newTitle !== art.title) { art.title = newTitle.slice(0, 200); titleChanged = true }

    const writeImage = async (field: 'cover'|'icon', f: any) => {
      const buf = await fs.readFile(f.filepath)
      if (!buf?.length) return
      const ext = getImageExtFromNameOrMime(f.filename, f.mimetype)
      if (!ext) { warnings.push({ field, error: 'image_unrecognized' }); return }
      const fname = `${field}.${ext}`
      ensureDirSync(art.publicPath)
      try {
        const files = await fs.readdir(art.publicPath).catch(() => [] as string[])
        for (const x of files) { if (x.startsWith(field + '.') && x !== fname) { try { await fs.unlink(path.join(art.publicPath, x)) } catch {} } }
      } catch {}
      await writeFileAtomic(art.publicPath, fname, buf)
      if (field === 'cover') { (art as any).cover = `${CONFIG.PUBLIC_URL_PREFIX}/${art.id}/${fname}`; coverWritten = true }
      if (field === 'icon') { (art as any).icon = `${CONFIG.PUBLIC_URL_PREFIX}/${art.id}/${fname}`; iconWritten = true }
      params.logger?.info?.({ id: art.id, field, fname, bytes: buf.length }, 'article.edit: wrote file')
    }

    for (const f of (params.files || [])) {
      try {
        if (f.fieldname === 'cover' && !coverWritten) await writeImage('cover', f)
        else if (f.fieldname === 'icon' && !iconWritten) await writeImage('icon', f)
      } finally {
        try { await fs.unlink(f.filepath) } catch {}
      }
    }

    if (titleChanged || coverWritten || iconWritten) art.updatedAt = new Date().toISOString()
    items[idx] = art
    await this.repo.writeMeta(items)
    return { item: art, warnings }
  }

  async upload(params: { title?: string; replace?: boolean; docx: Buffer; upName?: string; cover?: { buf: Buffer; filename?: string; mime?: string }; icon?: { buf: Buffer; filename?: string; mime?: string } }): Promise<{ item: ArticleMeta }> {
    const { title, replace, docx, upName, cover, icon } = params
    const hexSig = docx.subarray(0, 4).toString('hex')
    if (!(hexSig === '504b0304' || hexSig === '504b0506' || hexSig === '504b0708')) {
      const err: any = new Error('invalid_docx_zip')
      err.statusCode = 400
      throw err
    }
    const baseTitle = (title && title.trim()) || (upName ? upName.replace(/\.[^.]+$/, '') : 'Untitled')
    const slug = slugify(baseTitle)
    const createdAt = new Date().toISOString()
    const secureDir = path.join(CONFIG.SECURE_ROOT, slug)
    const publicDir = path.join(CONFIG.PUBLIC_ROOT, slug)
    try { ensureDirSync(secureDir) } catch { const e: any = new Error('server_storage_unavailable'); e.statusCode = 500; throw e }
    try { ensureDirSync(publicDir) } catch { const e: any = new Error('server_storage_unavailable'); e.statusCode = 500; throw e }

    const originalName = upName || `${slug}.docx`
    const storedName = `${Date.now()}-${originalName}`
    await fs.writeFile(path.join(secureDir, storedName), docx)

    let coverName: string | undefined
    let iconName: string | undefined
    if (cover?.buf?.length) {
      const ext = getImageExtFromNameOrMime(cover.filename, cover.mime)
      if (!ext) { const e: any = new Error('invalid_cover_image'); e.statusCode = 400; throw e }
      coverName = `cover.${ext}`
      await writeFileAtomic(publicDir, coverName, cover.buf)
    }
    if (icon?.buf?.length) {
      const ext = getImageExtFromNameOrMime(icon.filename, icon.mime)
      if (!ext) { const e: any = new Error('invalid_icon_image'); e.statusCode = 400; throw e }
      iconName = `icon.${ext}`
      await writeFileAtomic(publicDir, iconName, icon.buf)
    }

    // Convert DOCX to HTML
    const tmpBase = await fs.mkdtemp(path.join(os.tmpdir(), 'lwb-docx-'))
    const tmpDocx = path.join(tmpBase, originalName)
    try {
      await fs.writeFile(tmpDocx, docx)
      const result = await (MAMMOTH as any).convertToHtml({ path: tmpDocx })
      const unsafeHtml = result.value || ''
      const html = sanitizeHtml(unsafeHtml, { allowedTags: sanitizeHtml.defaults.allowedTags.concat(['img','iframe','figure','figcaption','a']), allowedAttributes: { '*': ['style'], 'img': ['src','alt'], 'iframe': ['src','allow','allowfullscreen','frameborder'], 'a': ['href','title','target','rel'] } })
      const styleCss = `/* minimal styles */\nbody{font-family:system-ui,Segoe UI,Roboto,Arial,sans-serif;max-width:820px;margin:2rem auto;padding:0 1rem;line-height:1.6;}h1,h2,h3{line-height:1.25}`
      const scriptJs = `// placeholder for article interactions`
      const indexHtml = `<!doctype html>\n<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${baseTitle}</title>${coverName ? `<link rel=\"preload\" as=\"image\" href=\"./${coverName}\">` : ''}<link rel="stylesheet" href="./style.css"></head><body>${html}<script src="./script.js" defer></script></body></html>`
      await fs.writeFile(path.join(publicDir, 'style.css'), styleCss, 'utf8')
      await fs.writeFile(path.join(publicDir, 'script.js'), scriptJs, 'utf8')
      await fs.writeFile(path.join(publicDir, 'index.html'), indexHtml, 'utf8')
    } finally {
      try { await fs.unlink(tmpDocx) } catch {}
      try { await fs.rmdir(tmpBase) } catch {}
    }

    const items = await this.repo.readMeta()
    const order = await this.repo.getNextOrder(items)
    const item: ArticleMeta = {
      id: slug,
      title: baseTitle,
      createdAt,
      updatedAt: createdAt,
      order,
      filename: originalName,
      securePath: secureDir,
      publicPath: publicDir,
      cover: coverName ? `${CONFIG.PUBLIC_URL_PREFIX}/${slug}/${coverName}` : undefined,
      icon: iconName ? `${CONFIG.PUBLIC_URL_PREFIX}/${slug}/${iconName}` : undefined,
    }
    const existingIdx = items.findIndex(i => i.id === slug)
    if (existingIdx >= 0 && !replace) {
      const e: any = new Error('article_exists')
      e.statusCode = 409
      e.details = { id: slug }
      throw e
    }
    if (existingIdx >= 0) items.splice(existingIdx, 1)
    items.push(item)
    await this.repo.writeMeta(items)
    return { item }
  }

  async reindex(): Promise<number> {
    const items = await this.repo.scanAndBuildMeta()
    await this.repo.writeMeta(items)
    return items.length
  }
}
