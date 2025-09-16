import path from 'path'
import fs from 'fs/promises'
import fssync from 'fs'
import { CONFIG } from '../config'
import { buildMenuRepository } from '../repositories/menuRepository'
import type { MenuItem } from '../types'
import { slugify } from '../utils/strings'
import { getImageExtFromNameOrMime } from '../utils/images'
import { Debug } from '../utils/debug'

type AddInput = { title: string; label: string; order?: number; icon?: { buf: Buffer; filename?: string; mime?: string } }
type EditInput = { title?: string; label?: string; icon?: { buf: Buffer; filename?: string; mime?: string } }

export class MenuService {
  private repo = buildMenuRepository()

  async list(): Promise<MenuItem[]> {
    const items = await this.repo.read()
    // Backfill icon URLs for items created before icon support where the file exists on disk
    const enriched = await Promise.all(items.map(async (it) => {
      if (!it.icon) {
        try {
          const dir = path.join(CONFIG.MENU_PUBLIC_DIR, it.id)
          // Try common extensions in order of preference
          const candidates = ['icon.png', 'icon.jpg', 'icon.jpeg', 'icon.webp']
          for (const name of candidates) {
            const full = path.join(dir, name)
            if (fssync.existsSync(full)) {
              it.icon = `${CONFIG.MENU_PUBLIC_URL_PREFIX}/${it.id}/${name}`
              break
            }
          }
        } catch {}
      }
      return it
    }))
    return enriched.slice().sort((a,b)=>a.order-b.order)
  }

  async add(input: AddInput): Promise<MenuItem> {
    const { title, label } = input
    const id = slugify(title || label)
    const now = new Date().toISOString()
    const items = await this.repo.read()
    if (items.find(i=>i.id===id)) {
      throw Object.assign(new Error('menu_exists'), { statusCode: 409 })
    }
    const order = typeof input.order === 'number' ? input.order : this.repo.nextOrder(items)
    let iconUrl: string | undefined
    if (input.icon?.buf?.length) {
      const ext = getImageExtFromNameOrMime(input.icon.filename, input.icon.mime) || 'png'
      const dir = path.join(CONFIG.MENU_PUBLIC_DIR, id)
      try { await fs.mkdir(dir, { recursive: true }) } catch {}
      const tmp = path.join(dir, `.icon.tmp-${process.pid}-${Date.now()}`)
      const final = path.join(dir, `icon.${ext}`)
      if (Debug.menuEnabled()) Debug.menu('add(): writing icon', { id, ext, dir, tmp, final, size: input.icon.buf.length })
      await fs.writeFile(tmp, input.icon.buf)
      await fs.rename(tmp, final)
      if (Debug.menuEnabled()) {
        try {
          const st = await fs.stat(final)
          Debug.menu('add(): wrote icon', { id, final, size: st.size, mtime: st.mtime.toISOString() })
        } catch (e) { Debug.menu('add(): stat final failed', { id, final, err: String(e) }) }
      }
      iconUrl = `${CONFIG.MENU_PUBLIC_URL_PREFIX}/${id}/icon.${ext}`
    }
    const item: MenuItem = { id, title, label, order, updatedAt: now, icon: iconUrl }
    items.push(item)
    await this.repo.write(items)
    return item
  }

  async remove(id: string) {
    const items = await this.repo.read()
    const idx = items.findIndex(i=>i.id===id)
    if (idx === -1) return
    items.splice(idx,1)
    // Re-number orders to keep sequence tight
    items.sort((a,b)=>a.order-b.order).forEach((it, i)=>{ it.order = i+1; it.updatedAt = new Date().toISOString() })
    await this.repo.write(items)
    // best-effort remove icon folder
    const dir = path.join(CONFIG.MENU_PUBLIC_DIR, id)
    try { await fs.rm(dir, { recursive: true, force: true }) } catch {}
  }

  async move(id: string, direction: 'up'|'down') {
    const items = await this.repo.read()
    const arr = items.slice().sort((a,b)=>a.order-b.order)
    const index = arr.findIndex(i=>i.id===id)
    if (index === -1) throw Object.assign(new Error('not_found'), { statusCode: 404 })
    if (direction === 'up' && index > 0) {
      const t = arr[index-1].order; arr[index-1].order = arr[index].order; arr[index].order = t
    } else if (direction === 'down' && index < arr.length - 1) {
      const t = arr[index+1].order; arr[index+1].order = arr[index].order; arr[index].order = t
    }
    arr.forEach(it => it.updatedAt = new Date().toISOString())
    await this.repo.write(arr)
  }

  async edit(id: string, input: EditInput): Promise<MenuItem> {
    const items = await this.repo.read()
    const item = items.find(i => i.id === id)
    if (!item) throw Object.assign(new Error('not_found'), { statusCode: 404 })

    let changed = false
    if (typeof input.title === 'string' && input.title !== item.title) {
      item.title = input.title
      changed = true
    }
    if (typeof input.label === 'string' && input.label !== item.label) {
      item.label = input.label
      changed = true
    }

    if (input.icon?.buf?.length) {
      const dir = path.join(CONFIG.MENU_PUBLIC_DIR, id)
      try { await fs.mkdir(dir, { recursive: true }) } catch {}
      // Clean up any existing icon.* to avoid stale extensions
      try {
        const entries = await fs.readdir(dir, { withFileTypes: true })
        if (Debug.menuEnabled()) Debug.menu('edit(): pre-clean entries', entries.map(e => e.name))
        await Promise.all(entries.filter(e => e.isFile() && /^icon\./.test(e.name)).map(e => fs.unlink(path.join(dir, e.name)).catch(() => {})))
      } catch {}
      const ext = getImageExtFromNameOrMime(input.icon.filename, input.icon.mime) || 'png'
      const tmp = path.join(dir, `.icon.tmp-${process.pid}-${Date.now()}`)
      const final = path.join(dir, `icon.${ext}`)
      if (Debug.menuEnabled()) Debug.menu('edit(): writing icon', { id, ext, dir, tmp, final, size: input.icon.buf.length })
      await fs.writeFile(tmp, input.icon.buf)
      await fs.rename(tmp, final)
      if (Debug.menuEnabled()) {
        try {
          const st = await fs.stat(final)
          Debug.menu('edit(): wrote icon', { id, final, size: st.size, mtime: st.mtime.toISOString() })
          const after = await fs.readdir(dir)
          Debug.menu('edit(): post-write entries', after)
        } catch (e) { Debug.menu('edit(): stat final failed', { id, final, err: String(e) }) }
      }
      item.icon = `${CONFIG.MENU_PUBLIC_URL_PREFIX}/${id}/icon.${ext}`
      changed = true
    }

    if (changed) {
      item.updatedAt = new Date().toISOString()
      await this.repo.write(items)
    }
    return item
  }
}
