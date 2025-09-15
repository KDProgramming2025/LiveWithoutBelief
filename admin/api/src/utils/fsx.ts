import path from 'path'
import fs from 'fs/promises'
import fssync from 'fs'

export function ensureDirSync(p: string) {
  if (!fssync.existsSync(p)) {
    fssync.mkdirSync(p, { recursive: true })
  }
}

export async function writeFileAtomic(dir: string, filename: string, buffer: Buffer) {
  // Write to a temp file in the same directory, fsync the file, then atomically rename
  ensureDirSync(dir)
  const tmp = path.join(dir, `.${filename}.tmp-${process.pid}-${Date.now()}-${Math.random().toString(16).slice(2)}`)
  const final = path.join(dir, filename)

  const fh = await fs.open(tmp, 'w')
  try {
    await fh.write(buffer, 0, buffer.length, 0)
    try { await fh.sync() } catch {}
  } finally {
    try { await fh.close() } catch {}
  }

  await fs.rename(tmp, final)

  try {
    const dh = await fs.open(dir, 'r')
    try { await dh.sync() } finally { await dh.close() }
  } catch {}
}
