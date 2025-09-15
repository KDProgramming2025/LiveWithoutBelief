import path from 'path'
import fs from 'fs/promises'
import fssync from 'fs'
import { CONFIG, metaDir } from '../config'
import type { ArticleMeta } from '../types'

export interface ArticleRepository {
  readMeta(): Promise<ArticleMeta[]>
  writeMeta(items: ArticleMeta[]): Promise<void>
  scanAndBuildMeta(): Promise<ArticleMeta[]>
  getNextOrder(items: ArticleMeta[]): Promise<number>
}

export function buildArticleRepository(): ArticleRepository {
  // Metadata cache to avoid transient empty reads on partial writes
  let metaCache: ArticleMeta[] = []
  let metaInitialized = false

  async function scanAndBuildMeta(): Promise<ArticleMeta[]> {
    const items: ArticleMeta[] = []
    try {
      const dirs = await fs.readdir(CONFIG.PUBLIC_ROOT, { withFileTypes: true })
      for (const d of dirs) {
        if (!d.isDirectory()) continue
        const slug = d.name
        const publicDir = path.join(CONFIG.PUBLIC_ROOT, slug)
        const secureDir = path.join(CONFIG.SECURE_ROOT, slug)
        const indexPath = path.join(publicDir, 'index.html')
        let title = slug
        try {
          const html = await fs.readFile(indexPath, 'utf8')
          const m = html.match(/<title>([^<]*)<\/title>/i)
          if (m) title = m[1]
        } catch {}
        const st = await fs.stat(publicDir).catch(() => null as any)
        const createdAt = st?.mtime?.toISOString?.() || new Date().toISOString()
        const updatedAt = createdAt
        const coverRel = fssync.existsSync(path.join(publicDir, 'cover.jpg')) ? 'cover.jpg'
          : fssync.existsSync(path.join(publicDir, 'cover.png')) ? 'cover.png'
          : undefined
        const iconRel = fssync.existsSync(path.join(publicDir, 'icon.jpg')) ? 'icon.jpg'
          : fssync.existsSync(path.join(publicDir, 'icon.png')) ? 'icon.png'
          : undefined
        items.push({
          id: slug,
          title,
          createdAt,
          updatedAt,
          order: items.length + 1,
          filename: `${slug}.docx`,
          securePath: secureDir,
          publicPath: publicDir,
          cover: coverRel ? `${CONFIG.PUBLIC_URL_PREFIX}/${slug}/${coverRel}` : undefined,
          icon: iconRel ? `${CONFIG.PUBLIC_URL_PREFIX}/${slug}/${iconRel}` : undefined,
        })
      }
    } catch (e) {
      // best-effort
    }
    return items
  }

  async function readMeta(): Promise<ArticleMeta[]> {
    try {
      const txt = await fs.readFile(CONFIG.META_FILE, 'utf8')
      const parsed = JSON.parse(txt) as ArticleMeta[]
      metaCache = parsed
      return parsed
    } catch (e) {
      if (!metaInitialized) {
        const scanned = await scanAndBuildMeta()
        if (scanned.length) {
          try { await writeMeta(scanned) } catch {}
          metaInitialized = true
          metaCache = scanned
          return scanned
        }
        metaInitialized = true
      }
      return metaCache
    }
  }

  async function writeMeta(items: ArticleMeta[]) {
    try { await fs.mkdir(metaDir(), { recursive: true }) } catch {}
    const tmp = CONFIG.META_FILE + '.tmp'
    const data = JSON.stringify(items, null, 2)
    await fs.writeFile(tmp, data, 'utf8')
    await fs.rename(tmp, CONFIG.META_FILE)
    metaCache = items
  }

  async function getNextOrder(items: ArticleMeta[]) { return items.length ? Math.max(...items.map(i => i.order)) + 1 : 1 }

  return { readMeta, writeMeta, scanAndBuildMeta, getNextOrder }
}
