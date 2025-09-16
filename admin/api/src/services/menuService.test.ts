import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import fs from 'fs/promises'
import path from 'path'
import { MenuService } from './menuService'
import { CONFIG } from '../config'

const TMP_DIR = path.join(process.cwd(), 'tmp', 'vitest-menu')

describe('MenuService icon write (smoke)', () => {
  const svc = new MenuService()
  const oldDir = CONFIG.MENU_PUBLIC_DIR
  const testId = 'test-icon'
  const iconBuf = Buffer.alloc(256, 1)

  beforeAll(async () => {
    // Redirect public dir to tmp for test
    ;(CONFIG as any).MENU_PUBLIC_DIR = TMP_DIR
    await fs.rm(TMP_DIR, { recursive: true, force: true })
    await fs.mkdir(TMP_DIR, { recursive: true })
  })

  afterAll(async () => {
    ;(CONFIG as any).MENU_PUBLIC_DIR = oldDir
    await fs.rm(TMP_DIR, { recursive: true, force: true })
  })

  it('writes icon during add and edit', async () => {
    const item = await svc.add({ title: testId, label: 'lbl', icon: { buf: iconBuf, filename: 'x.png', mime: 'image/png' } })
    expect(item.icon).toBeTruthy()
    const idDir = path.join(TMP_DIR, item.id)
    const files = await fs.readdir(idDir)
    expect(files.some(f => f.startsWith('icon.'))).toBe(true)

    const filePath = path.join(idDir, files.find(f => f.startsWith('icon.'))!)
    const st1 = await fs.stat(filePath)
    expect(st1.size).toBe(256)

    // Now edit with different buffer size
    const buf2 = Buffer.alloc(512, 2)
    const edited = await svc.edit(item.id, { icon: { buf: buf2, filename: 'z.webp', mime: 'image/webp' } })
    expect(edited.icon).toContain('/' + item.id + '/')
    const files2 = await fs.readdir(idDir)
    expect(files2.some(f => f.startsWith('icon.'))).toBe(true)
    const filePath2 = path.join(idDir, files2.find(f => f.startsWith('icon.'))!)
    const st2 = await fs.stat(filePath2)
    expect(st2.size).toBe(512)
  })
})
