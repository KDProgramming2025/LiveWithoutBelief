import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import path from 'path'
import fs from 'fs/promises'
import fssync from 'fs'
import { MenuService } from '../src/services/menuService'
import { CONFIG } from '../src/config'

describe('MenuService.list backfills icon URLs', () => {
  const testId = 'test-backfill-icon'
  const itemDir = path.join(CONFIG.MENU_PUBLIC_DIR, testId)
  const metaFile = CONFIG.MENU_META_FILE
  const metaBackup = metaFile + '.bak'

  beforeAll(async () => {
    // backup meta if exists
    try { await fs.copyFile(metaFile, metaBackup) } catch {}
    // write meta with an item lacking icon
    await fs.mkdir(path.dirname(metaFile), { recursive: true })
    await fs.writeFile(metaFile, JSON.stringify([{ id: testId, title: 'T', label: 'L', order: 1, updatedAt: new Date().toISOString() }], null, 2), 'utf8')
    // create a matching icon file on disk
    await fs.mkdir(itemDir, { recursive: true })
    await fs.writeFile(path.join(itemDir, 'icon.png'), Buffer.from([0x89,0x50,0x4e,0x47]))
  })

  afterAll(async () => {
    try { await fs.unlink(path.join(itemDir, 'icon.png')) } catch {}
    try { await fs.rmdir(itemDir) } catch {}
    // restore meta
    try { await fs.copyFile(metaBackup, metaFile) } catch {}
    try { await fs.unlink(metaBackup) } catch {}
  })

  it('returns items with icon URL when file exists', async () => {
    const svc = new MenuService()
    const items = await svc.list()
    const it = items.find(x => x.id === testId)
    expect(it).toBeTruthy()
    expect(it?.icon).toBeTruthy()
    expect(String(it?.icon)).toContain(`${CONFIG.MENU_PUBLIC_URL_PREFIX}/${testId}/icon.png`)
  })
})
