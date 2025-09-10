import { describe, test, expect, vi } from 'vitest';

// We will exercise parseDocx and a new buildManifest helper to ensure manifest has checksums and signature
vi.mock('mammoth', () => ({
  default: {
    images: { inline: (h: (img: { contentType?: string; read: (e: 'base64'|'binary') => Promise<string> }) => unknown) => h },
    convertToHtml: async () => ({ value: '<h1>A</h1><p>hello</p><a href="https://youtu.be/xyz">yt</a>' })
  }
}));

describe('manifest and sanitization', () => {
  test('parseDocx returns sanitized html and plain text', async () => {
    const { parseDocx } = await import('./docx.js');
    const res = await parseDocx('x.docx', { withHtml: true });
    expect(res.wordCount).toBeGreaterThan(0);
    // Basic shape checks
    expect(Array.isArray(res.sections)).toBe(true);
    // Sanitized HTML should not contain script/style tags
    const htmlConcat = res.sections.map(s => s.html || '').join('');
    expect(/<script/i.test(htmlConcat)).toBe(false);
  });

  test('builds signed manifest with checksums', async () => {
    const { parseDocx } = await import('./docx.js');
    const { buildManifest } = await import('./manifest.js');
    const parsed = await parseDocx('x.docx', { withHtml: true });
    const manifest = buildManifest({
      id: 'doc-1',
      version: 1,
      title: 'Doc 1',
      sections: parsed.sections,
      media: parsed.media,
    }, 'test-secret');
    expect(manifest.id).toBe('doc-1');
    expect(manifest.version).toBe(1);
    expect(typeof manifest.checksum).toBe('string');
    expect(manifest.checksum.length).toBe(64);
    expect(typeof manifest.signature).toBe('string');
  });
});
