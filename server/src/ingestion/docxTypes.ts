export interface ExtractedMediaItem {
  type: 'image' | 'audio' | 'video' | 'embed';
  id: string; // original relationship id or generated
  filename?: string;
  contentType?: string;
  data?: Buffer; // only when requested
  src?: string; // extracted src for embeds
  checksum?: string; // hex sha256
}

export interface ParsedDocxSection {
  kind: 'heading' | 'paragraph' | 'list' | 'quote' | 'image' | 'audio' | 'video' | 'embed';
  level?: number; // for headings
  text?: string; // plain text content
  html?: string; // sanitized html when available
  mediaRefId?: string; // reference id for media items
}

export interface ParseDocxOptions {
  withHtml?: boolean;
  extractMediaData?: boolean; // include Buffer data for binary assets
}

export interface ParsedDocxResult {
  sections: ParsedDocxSection[];
  media: ExtractedMediaItem[];
  wordCount: number;
}
