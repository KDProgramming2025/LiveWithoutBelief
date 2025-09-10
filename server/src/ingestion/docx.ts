import crypto from 'crypto';
import sanitizeHtml from 'sanitize-html';
import mammothDefault from 'mammoth';
import { ParsedDocxResult, ParseDocxOptions, ParsedDocxSection, ExtractedMediaItem } from './docxTypes.js';
import type { ImageHandler } from 'mammoth';

// Very small HTML -> sections parser: recognize headings, paragraphs, lists, blockquotes, and embedded iframes
function htmlToSections(html: string): ParsedDocxSection[] {
  const sections: ParsedDocxSection[] = [];
  // Scan with regex for key blocks
  const re = /<(h[1-6]|p|ul|ol|blockquote|iframe)([^>]*)>([\s\S]*?)<\/\1>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html)) !== null) {
    const tag = m[1].toLowerCase();
    const inner = m[3];
    if (tag.startsWith('h')) {
      const level = Number(tag.substring(1));
      sections.push({ kind: 'heading', level, text: stripTags(inner).trim(), html: undefined });
    } else if (tag === 'p') {
      const text = stripTags(inner).trim();
      if (text) sections.push({ kind: 'paragraph', text });
    } else if (tag === 'blockquote') {
      const text = stripTags(inner).trim();
      if (text) sections.push({ kind: 'quote', text });
    } else if (tag === 'ul' || tag === 'ol') {
      // collapse list items into single section with newline separated items (simple for MVP)
      const items = Array.from(inner.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)).map((mm) => stripTags(mm[1]).trim()).filter(Boolean);
      if (items.length) sections.push({ kind: 'list', text: items.join('\n') });
    } else if (tag === 'iframe') {
      const srcMatch = (m[2] || inner).match(/src=["']([^"']+)["']/i);
      const src = srcMatch?.[1];
      if (src) sections.push({ kind: 'embed', text: src });
    }
  }
  return sections;
}

function stripTags(html: string): string { return html.replace(/<[^>]+>/g, ''); }

function checksum(buf: Buffer): string { return crypto.createHash('sha256').update(buf).digest('hex'); }

export async function parseDocx(filePath: string, opts: ParseDocxOptions = {}): Promise<ParsedDocxResult> {
  // Use mammoth to convert .docx to HTML, and capture images via convertImage callback
  const images: ExtractedMediaItem[] = [];
  // Support both ESM default with props and named exports shim
  const mammoth = mammothDefault as unknown as {
    images: { inline: (handler: ImageHandler) => ImageHandler };
    convertToHtml: (input: { path: string }, options?: { convertImage?: ImageHandler }) => Promise<{ value: string }>;
  };
  const result = await mammoth.convertToHtml({ path: filePath }, {
    convertImage: mammoth.images.inline(async (image: { contentType?: string; read: (encoding: 'base64' | 'binary') => Promise<string> }) => {
      const buffer = await image.read('base64');
      const data = Buffer.from(buffer, 'base64');
      const contentType = image.contentType as string | undefined;
      const ext = contentType?.split('/')[1] || 'bin';
      const id = crypto.randomBytes(8).toString('hex');
      const filename = `image-${id}.${ext}`;
      const item: ExtractedMediaItem = { type: 'image', id, filename, contentType, data: opts.extractMediaData ? data : undefined, checksum: checksum(data) };
      images.push(item);
      // Return sanitized <img>
      return { src: `data:${contentType};base64,${buffer}` };
    })
  });
  const unsafeHtml = result.value || '';
  const html = opts.withHtml ? sanitizeHtml(unsafeHtml, { allowedTags: sanitizeHtml.defaults.allowedTags.concat(['img','iframe','figure','figcaption','a']), allowedAttributes: { '*': ['style'], 'img': ['src','alt'], 'iframe': ['src','allow','allowfullscreen','frameborder'], 'a': ['href','title','target','rel'] } }) : '';
  const textOnly = stripTags(unsafeHtml);
  const words = textOnly.trim().split(/\s+/).filter(Boolean);
  const sections = htmlToSections(html || unsafeHtml);
  // Detect anchor links for YouTube and audio files in the sanitized or raw HTML
  const allHtml = (html || unsafeHtml);
  const anchorRe = /<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let am: RegExpExecArray | null;
  while ((am = anchorRe.exec(allHtml)) !== null) {
    const href = am[1];
  if (/youtu\.be\//i.test(href) || /youtube\.com\/(watch\?v=|embed\/)/i.test(href)) {
      sections.push({ kind: 'embed', text: href });
      const id = crypto.randomBytes(8).toString('hex');
      images.push({ type: 'embed', id, src: href, checksum: checksum(Buffer.from(href, 'utf8')) });
    } else if (/\.(mp3|wav|m4a)(\?|#|$)/i.test(href)) {
      sections.push({ kind: 'audio', text: href });
      const id = crypto.randomBytes(8).toString('hex');
      images.push({ type: 'audio', id, src: href, checksum: checksum(Buffer.from(href, 'utf8')) });
    }
  }
  return { sections, media: images, wordCount: words.length, html, text: textOnly };
}

export async function extractMedia(filePath: string): Promise<ExtractedMediaItem[]> {
  // For MVP rely on parseDocx image capture; future: unzip and traverse word/media
  const parsed = await parseDocx(filePath, { extractMediaData: true });
  return parsed.media;
}
