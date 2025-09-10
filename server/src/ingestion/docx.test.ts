import { describe, test, expect, vi } from 'vitest';

// Mock mammoth to return deterministic HTML so tests are hermetic
vi.mock('mammoth', () => ({
  default: {
    images: { inline: (handler: (img: { contentType?: string; read: (enc: 'base64'|'binary') => Promise<string> }) => unknown) => handler },
    convertToHtml: async () => ({ value: '<h1>Title</h1><p>Hello world</p><ul><li>A</li><li>B</li></ul>' })
  }
}));

describe('parseDocx', () => {
  test('parses headings, paragraphs, lists and wordCount', async () => {
    const { parseDocx } = await import('./docx.js');
    const res = await parseDocx('ignored.docx', { withHtml: true });
    expect(res.wordCount).toBeGreaterThan(0);
    expect(res.sections.length).toBeGreaterThan(0);
    const kinds = new Set(res.sections.map(s => s.kind));
    expect(kinds.has('heading')).toBe(true);
    expect(kinds.has('paragraph')).toBe(true);
    expect(kinds.has('list')).toBe(true);
  });
});

describe('extractMedia', () => {
  test('returns array and media items include checksum when present', async () => {
    const { extractMedia } = await import('./docx.js');
    const media = await extractMedia('ignored.docx');
    expect(Array.isArray(media)).toBe(true);
    for (const m of media) {
      if (m.data) {
        expect(m.checksum).toBeDefined();
        expect(m.checksum!.length).toBe(64);
      }
    }
  });
});
