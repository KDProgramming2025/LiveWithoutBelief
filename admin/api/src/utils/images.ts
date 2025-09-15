export function getImageExtFromNameOrMime(filename?: string, mimetype?: string): 'jpg'|'png'|'gif'|'webp'|undefined {
  const name = filename?.toLowerCase() || ''
  const mime = mimetype?.toLowerCase() || ''
  const byExt = (() => {
    const ext = name.includes('.') ? name.substring(name.lastIndexOf('.') + 1) : ''
    if (ext === 'jpeg') return 'jpg' as const
    if (ext === 'jpg' || ext === 'png' || ext === 'gif' || ext === 'webp') return ext as any
    return undefined
  })()
  if (byExt) return byExt
  if (mime === 'image/jpeg' || mime === 'image/jpg') return 'jpg'
  if (mime === 'image/png') return 'png'
  if (mime === 'image/gif') return 'gif'
  if (mime === 'image/webp') return 'webp'
  return undefined
}
