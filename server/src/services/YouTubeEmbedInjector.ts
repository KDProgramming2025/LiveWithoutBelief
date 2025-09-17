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

  // Parse relationships for YouTube targets (some versions store YouTube URL only inside webVideoPr embeddedHtml)
  const rels: Array<{ id: string; target: string }> = []
  const relRe = /<Relationship\b([^>]+?)\/>/g
  let rm: RegExpExecArray | null
  while ((rm = relRe.exec(relsXml)) !== null) {
    const attrs = rm[1]
    const id = /\bId="([^"]+)"/.exec(attrs)?.[1]
    const target = /\bTarget="([^"]+)"/.exec(attrs)?.[1]
    if (id && target && /youtu/i.test(target)) rels.push({ id, target })
  }
  let newDocXml = docXml
  const embeds: YouTubePlaceholder[] = []

  // Unified pass: find any <w:drawing> blocks that contain either a relationship-based r:id referencing a YouTube target
  // or an embedded <wp15:webVideoPr ... embeddedHtml="...youtube..."> element. We will append a marker paragraph *after*
  // the drawing instead of replacing the original paragraph to avoid truncating the document.
  // Provided user regex pattern (adapted to JS): <w:drawing(?:(?!<w:drawing).)*webVideoPr.*?</w:drawing>

  // Pre-build a map rId -> YouTube target data.
  const ytRelMap = new Map<string, { target: string; videoId: string | null }>()
  for (const rel of rels) ytRelMap.set(rel.id, { target: rel.target, videoId: deriveVideoId(rel.target) })

  const drawingGlobalRe = /<w:drawing[\s\S]*?<\/w:drawing>/g
  let dm: RegExpExecArray | null
  const processedPositions = new Set<number>()
  while ((dm = drawingGlobalRe.exec(newDocXml)) !== null) {
    const block = dm[0]
    const blockStart = dm.index
    const blockEnd = blockStart + block.length
    if (processedPositions.has(blockEnd)) continue

    let videoId: string | null = null
    let url: string | null = null

    // 1) Relationship id reference
    const relIdMatch = /r:id="(rId[0-9A-Za-z]+)"/.exec(block)
    if (relIdMatch) {
      const relInfo = ytRelMap.get(relIdMatch[1])
      if (relInfo) {
        videoId = relInfo.videoId
        url = relInfo.target
      }
    }

    // 2) Embedded webVideoPr detection inside this drawing
    if (!url) {
      const webVideoTag = /<wp15:webVideoPr\b[^>]*embeddedHtml="([^"]+)"[^>]*\/>/i.exec(block)
      if (webVideoTag) {
        const raw = webVideoTag[1]
        const decoded = raw.replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&amp;/g,'&')
        const srcMatch = /src="(https?:\/\/[^"']+)"/.exec(decoded)
        if (srcMatch && /youtu/i.test(srcMatch[1])) {
          url = srcMatch[1]
          videoId = deriveVideoId(url) || videoId
        }
      }
    }

    if (!url) continue // not a YouTube drawing
    const finalVideoId = videoId || 'yt'
    const placeholder = `[[LWB_YT:${finalVideoId}]]`

    // Append marker paragraph AFTER the drawing's parent paragraph boundary if possible; else right after drawing tag.
    // Find closing parent paragraph end.
    const paraClose = newDocXml.indexOf('</w:p>', blockEnd)
    const paraStart = newDocXml.lastIndexOf('<w:p', blockStart)
    let insertAt = blockEnd
    if (paraStart !== -1 && paraClose !== -1 && paraStart < blockStart && paraClose > blockEnd) {
      // Insert before paragraph close to keep block and placeholder grouped.
      insertAt = paraClose
    }
    const inject = `<w:p><w:r><w:t xml:space="preserve">${placeholder}</w:t></w:r></w:p>`
    newDocXml = newDocXml.slice(0, insertAt) + inject + newDocXml.slice(insertAt)
    processedPositions.add(insertAt + inject.length)
    embeds.push({ videoId: finalVideoId, url, placeholder })
    // Adjust regex lastIndex due to insertion shift
    drawingGlobalRe.lastIndex = insertAt + inject.length
  }

  if (embeds.length === 0) return { embeds: [] }
  zip.file(docPath, newDocXml)
  const modified = await zip.generateAsync({ type: 'nodebuffer' }) as unknown as Buffer
  return { embeds, modifiedBuffer: modified }
}
