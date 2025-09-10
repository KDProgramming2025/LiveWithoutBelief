import crypto from 'crypto';
import type { ExtractedMediaItem, ParsedDocxSection } from './docxTypes.js';

export interface ArticleManifest {
  id: string;
  title: string;
  version: number;
  wordCount?: number;
  sectionsCount: number;
  mediaCount: number;
  checksum: string; // sha256 over normalized content
  signature: string; // HMAC-SHA256 over checksum using secret
}

export function buildManifest(input: {
  id: string; title: string; version: number; sections: ParsedDocxSection[]; media: ExtractedMediaItem[]; wordCount?: number;
}, secret: string): ArticleManifest {
  const normalized = JSON.stringify({
    id: input.id,
    version: input.version,
    sections: input.sections.map(s => ({ k: s.kind, l: s.level, t: s.text, m: s.mediaRefId })),
    media: input.media.map(m => ({ t: m.type, f: m.filename, c: m.checksum, s: m.src })),
  });
  const checksum = crypto.createHash('sha256').update(normalized).digest('hex');
  const signature = crypto.createHmac('sha256', secret).update(checksum).digest('hex');
  return {
    id: input.id,
    title: input.title,
    version: input.version,
    wordCount: input.wordCount,
    sectionsCount: input.sections.length,
    mediaCount: input.media.length,
    checksum,
    signature,
  };
}
