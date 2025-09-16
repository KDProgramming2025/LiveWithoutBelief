import { Debug } from './debug'

export async function readPartBuffer(part: any): Promise<{ buffer: Buffer; filename?: string; mimetype?: string }> {
  if (!part) return { buffer: Buffer.alloc(0), filename: undefined }
  if (part.file && typeof part.file === 'object' && typeof (part.file as any)[Symbol.asyncIterator] === 'function') {
    const bufs: Buffer[] = []
    for await (const ch of part.file as any) bufs.push(Buffer.isBuffer(ch) ? ch as Buffer : Buffer.from(ch))
    const out = { buffer: Buffer.concat(bufs), filename: part.filename as string | undefined, mimetype: part.mimetype as string | undefined }
    if (Debug.multipartEnabled()) Debug.multipart('readPartBuffer(iterable file)', { filename: out.filename, mimetype: out.mimetype, size: out.buffer.length })
    return out
  }
  if (typeof part.toBuffer === 'function') {
    const buf: Buffer = await part.toBuffer()
    const out = { buffer: buf, filename: part.filename as string | undefined, mimetype: part.mimetype as string | undefined }
    if (Debug.multipartEnabled()) Debug.multipart('readPartBuffer(toBuffer)', { filename: out.filename, mimetype: out.mimetype, size: out.buffer.length })
    return out
  }
  if (part.value) {
    const val = part.value as any
    const buf = Buffer.isBuffer(val) ? val : Buffer.from(String(val))
    const out = { buffer: buf, filename: part.filename as string | undefined, mimetype: part.mimetype as string | undefined }
    if (Debug.multipartEnabled()) Debug.multipart('readPartBuffer(value)', { filename: out.filename, mimetype: out.mimetype, size: out.buffer.length })
    return out
  }
  if (typeof (part as any)[Symbol.asyncIterator] === 'function') {
    const bufs: Buffer[] = []
    for await (const ch of part as any) bufs.push(Buffer.isBuffer(ch) ? ch as Buffer : Buffer.from(ch))
    const out = { buffer: Buffer.concat(bufs), filename: (part as any).filename, mimetype: (part as any).mimetype }
    if (Debug.multipartEnabled()) Debug.multipart('readPartBuffer(asyncIterator)', { filename: out.filename, mimetype: out.mimetype, size: (out.buffer as Buffer).length })
    return out
  }
  const out = { buffer: Buffer.alloc(0), filename: part.filename as string | undefined, mimetype: part.mimetype as string | undefined }
  if (Debug.multipartEnabled()) Debug.multipart('readPartBuffer(empty-fallback)', { filename: out.filename, mimetype: out.mimetype, size: 0 })
  return out
}
