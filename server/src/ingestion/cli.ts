#!/usr/bin/env node
import { parseDocx } from './docx.js';

async function main() {
  const [cmd, file, ...rest] = process.argv.slice(2);
  if (!cmd || cmd === '--help' || cmd === '-h') {
    console.log('Usage: tsx src/ingestion/cli.ts parse <file.docx> [--html]');
    process.exit(0);
  }
  if (cmd !== 'parse') {
    console.error(`Unknown command: ${cmd}`);
    process.exit(1);
  }
  if (!file) {
    console.error('Missing <file.docx>');
    process.exit(1);
  }
  const withHtml = rest.includes('--html');
  try {
    const res = await parseDocx(file, { withHtml });
    const out = {
      file,
      wordCount: res.wordCount,
      sections: res.sections.slice(0, 10),
      sectionsTotal: res.sections.length,
      media: res.media.map(m => ({ type: m.type, filename: m.filename, contentType: m.contentType, src: m.src, checksum: m.checksum })).slice(0, 10),
      mediaTotal: res.media.length
    };
    console.log(JSON.stringify(out, null, 2));
  } catch (e) {
    console.error('Failed to parse:', (e as Error).message);
    process.exit(2);
  }
}

main();
