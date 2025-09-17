import JSZip from 'jszip'

export interface YouTubePlaceholder {
  videoId: string
  url: string
  placeholder: string
}

export interface YouTubeInjectionResult {
  modifiedBuffer?: Buffer
  embeds: YouTubePlaceholder[]
}

// Derive video id from common YouTube URL formats
function deriveVideoId(url: string): string | null {
  try {
    const u = new URL(url)
    if (/youtu\.be$/i.test(u.hostname)) return u.pathname.replace(/^\//,'') || null
    if (u.hostname.includes('youtube.com')) {
      if (u.pathname.startsWith('/watch')) return u.searchParams.get('v')
      if (u.pathname.startsWith('/embed/')) return u.pathname.split('/')[2] || null
    }
    return null
  } catch { return null }
}

function escapeRegExp(s: string){ return s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&') }

/**
 * Inject placeholders after drawing blocks that reference YouTube embed relationships.
 * We search for relationships whose Target contains 'youtu', then locate r:id usage inside
 * <w:drawing> (via a:hlinkClick or generic r:id) and append a marker paragraph after it.
 */
export async function injectYouTubePlaceholders(originalDocx: Buffer): Promise<YouTubeInjectionResult> {
  const zip = await JSZip.loadAsync(originalDocx)
  const relsPath = 'word/_rels/document.xml.rels'
  const docPath = 'word/document.xml'
  const relsXml = await zip.file(relsPath)?.async('string')
  const docXml = await zip.file(docPath)?.async('string')
  if (!relsXml || !docXml) return { embeds: [] }

  // Parse relationships
  const rels: Array<{ id: string; target: string }> = []
  const relRe = /<Relationship\b([^>]+?)\/>/g
  let rm: RegExpExecArray | null
  while ((rm = relRe.exec(relsXml)) !== null) {
    const attrs = rm[1]
    const id = /\bId="([^"]+)"/.exec(attrs)?.[1]
    const target = /\bTarget="([^"]+)"/.exec(attrs)?.[1]
    if (id && target && /youtu/i.test(target)) rels.push({ id, target })
  }
  if (rels.length === 0) return { embeds: [] }

  let newDocXml = docXml
  const embeds: YouTubePlaceholder[] = []
  for (const rel of rels) {
    const videoId = deriveVideoId(rel.target)
    const placeholder = `[[LWB_YT:${videoId || rel.id}]]`
    // Find occurrences referencing this rId within drawings
    // Pattern: <w:drawing> ... r:id="rIdX" ... </w:drawing>
    const drawingRe = new RegExp(`<w:drawing[\s\S]*?r:id="${escapeRegExp(rel.id)}"[\s\S]*?</w:drawing>`, 'g')
    let dm: RegExpExecArray | null
    const already = new Set<number>()
    while ((dm = drawingRe.exec(newDocXml)) !== null) {
      const endPos = dm.index + dm[0].length
      if (already.has(endPos)) continue
      already.add(endPos)
      // Inject marker paragraph after the drawing block
      const inject = `<w:p><w:r><w:t xml:space="preserve">${placeholder}</w:t></w:r></w:p>`
      newDocXml = newDocXml.slice(0, endPos) + inject + newDocXml.slice(endPos)
      embeds.push({ videoId: videoId || rel.id, url: rel.target, placeholder })
      // Adjust regex lastIndex to continue after injected content
      drawingRe.lastIndex = endPos + inject.length
    }
  }

  if (embeds.length === 0) return { embeds: [] }
  zip.file(docPath, newDocXml)
  const modified = await zip.generateAsync({ type: 'nodebuffer' }) as unknown as Buffer
  return { embeds, modifiedBuffer: modified }
}
