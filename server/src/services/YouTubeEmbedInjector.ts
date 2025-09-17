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

  // 1. Relationship-based detection (existing logic)
  for (const rel of rels) {
    const videoId = deriveVideoId(rel.target)
    const placeholder = `[[LWB_YT:${videoId || rel.id}]]`
    const drawingRe = new RegExp(`<w:drawing[\s\S]*?r:id="${escapeRegExp(rel.id)}"[\s\S]*?</w:drawing>`, 'g')
    let dm: RegExpExecArray | null
    while ((dm = drawingRe.exec(newDocXml)) !== null) {
      const drawStart = dm.index
      const drawEnd = dm.index + dm[0].length
      const paraStart = newDocXml.lastIndexOf('<w:p', drawStart)
      if (paraStart === -1) continue
      const paraClose = newDocXml.indexOf('</w:p>', drawEnd)
      if (paraClose === -1) continue
      const paraEnd = paraClose + '</w:p>'.length
      const replacement = `<w:p><w:r><w:t xml:space="preserve">${placeholder}</w:t></w:r></w:p>`
      newDocXml = newDocXml.slice(0, paraStart) + replacement + newDocXml.slice(paraEnd)
      embeds.push({ videoId: videoId || rel.id, url: rel.target, placeholder })
      drawingRe.lastIndex = paraStart + replacement.length
    }
  }

  // 2. Embedded webVideoPr detection (handles case where YouTube URL only appears inside embeddedHtml attribute)
  // Pattern example: <wp15:webVideoPr ... embeddedHtml="&lt;iframe ... src=&quot;https://www.youtube.com/embed/VIDEOID?...&quot; ... &gt;&lt;/iframe&gt;" .../>
  const webVideoRe = /<wp15:webVideoPr\b[^>]*embeddedHtml="([^"]+)"[^>]*\/>/g
  let wvm: RegExpExecArray | null
  while ((wvm = webVideoRe.exec(newDocXml)) !== null) {
    const raw = wvm[1]
    // Decode minimal XML entities required to locate src
    const decoded = raw.replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&amp;/g,'&')
    const srcMatch = /src="(https?:\/\/[^"']+)"/.exec(decoded)
    if (!srcMatch) continue
    const srcUrl = srcMatch[1]
    if (!/youtu/i.test(srcUrl)) continue
    const vid = deriveVideoId(srcUrl) || 'yt'
    const placeholder = `[[LWB_YT:${vid}]]`
    // Find paragraph containing this webVideoPr tag occurrence.
    const tagStart = wvm.index
    const tagEnd = wvm.index + wvm[0].length
    const paraStart = newDocXml.lastIndexOf('<w:p', tagStart)
    if (paraStart === -1) continue
    const paraClose = newDocXml.indexOf('</w:p>', tagEnd)
    if (paraClose === -1) continue
    const paraEnd = paraClose + '</w:p>'.length
    // Avoid duplicate insertion if a relationship-based pass already replaced this paragraph (contains placeholder)
    const existingPara = newDocXml.slice(paraStart, paraEnd)
    if (existingPara.includes('[[LWB_YT:')) continue
    const replacement = `<w:p><w:r><w:t xml:space="preserve">${placeholder}</w:t></w:r></w:p>`
    newDocXml = newDocXml.slice(0, paraStart) + replacement + newDocXml.slice(paraEnd)
    embeds.push({ videoId: vid, url: srcUrl, placeholder })
    // Adjust regex search position after modification
    webVideoRe.lastIndex = paraStart + replacement.length
  }

  if (embeds.length === 0) return { embeds: [] }
  zip.file(docPath, newDocXml)
  const modified = await zip.generateAsync({ type: 'nodebuffer' }) as unknown as Buffer
  return { embeds, modifiedBuffer: modified }
}
