export async function readPartBuffer(part: any): Promise<{ buffer: Buffer; filename?: string; mimetype?: string }> {
  if (!part) return { buffer: Buffer.alloc(0), filename: undefined }
  if (part.file && typeof part.file === 'object' && typeof (part.file as any)[Symbol.asyncIterator] === 'function') {
    const bufs: Buffer[] = []
    for await (const ch of part.file as any) bufs.push(Buffer.isBuffer(ch) ? ch as Buffer : Buffer.from(ch))
    return { buffer: Buffer.concat(bufs), filename: part.filename as string | undefined, mimetype: part.mimetype as string | undefined }
  }
  if (typeof part.toBuffer === 'function') {
    const buf: Buffer = await part.toBuffer()
    return { buffer: buf, filename: part.filename as string | undefined, mimetype: part.mimetype as string | undefined }
  }
  if (part.value) {
    const val = part.value as any
    const buf = Buffer.isBuffer(val) ? val : Buffer.from(String(val))
    return { buffer: buf, filename: part.filename as string | undefined, mimetype: part.mimetype as string | undefined }
  }
  if (typeof (part as any)[Symbol.asyncIterator] === 'function') {
    const bufs: Buffer[] = []
    for await (const ch of part as any) bufs.push(Buffer.isBuffer(ch) ? ch as Buffer : Buffer.from(ch))
    return { buffer: Buffer.concat(bufs), filename: (part as any).filename, mimetype: (part as any).mimetype }
  }
  return { buffer: Buffer.alloc(0), filename: part.filename as string | undefined, mimetype: part.mimetype as string | undefined }
}
