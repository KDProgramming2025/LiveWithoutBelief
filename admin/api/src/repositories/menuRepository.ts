import path from 'path'
import fs from 'fs/promises'
import fssync from 'fs'
import { CONFIG } from '../config'
import type { MenuItem } from '../types'

export interface MenuRepository {
  read(): Promise<MenuItem[]>
  write(items: MenuItem[]): Promise<void>
  nextOrder(items: MenuItem[]): number
}

export function buildMenuRepository(): MenuRepository {
  let cache: MenuItem[] = []

  async function read(): Promise<MenuItem[]> {
    try {
      const txt = await fs.readFile(CONFIG.MENU_META_FILE, 'utf8')
      cache = JSON.parse(txt) as MenuItem[]
      return cache
    } catch {
      return cache
    }
  }

  async function write(items: MenuItem[]) {
    const dir = path.dirname(CONFIG.MENU_META_FILE)
    try { await fs.mkdir(dir, { recursive: true }) } catch {}
    const tmp = CONFIG.MENU_META_FILE + '.tmp'
    await fs.writeFile(tmp, JSON.stringify(items, null, 2), 'utf8')
    await fs.rename(tmp, CONFIG.MENU_META_FILE)
    cache = items
  }

  function nextOrder(items: MenuItem[]) { return items.length ? Math.max(...items.map(i => i.order)) + 1 : 1 }

  return { read, write, nextOrder }
}
