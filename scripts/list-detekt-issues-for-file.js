#!/usr/bin/env node
/**
 * Script: list-detekt-issues-for-file.js
 * Description:
 *  Runs detektAll and filters violation lines for a specific Kotlin file path passed as first argument (relative or absolute).
 *  Prints rule id, line number, and message for each finding to stdout for targeted cleanup.
 */
const { spawn } = require('child_process');
const { relative, resolve, basename } = require('path');
const { writeFileSync } = require('fs');

const PROJECT_ROOT = process.cwd();
// Args: <file>
const args = process.argv.slice(2);
const targetArg = args[0];
if (!targetArg || targetArg.startsWith('-')) {
  console.error('Usage: node scripts/list-detekt-issues-for-file.js <relative-or-absolute-kt-file>');
  process.exit(1);
}
const absTarget = resolve(PROJECT_ROOT, targetArg);
const relTarget = relative(PROJECT_ROOT, absTarget).replace(/\\/g, '/');

// Accept multiple output shapes, including:
// <path>.kt:<line>:<col>: <RuleId>: <Message>
// <path>.kt:<line>:<col>: <Message> [RuleId]
// We'll try both regexes.
const classicRegex = /(.*\.kt):(\d+):(\d+):\s+([A-Za-z0-9_.-]+):\s+(.*)$/;
const bracketRegex = /(.*\.kt):(\d+):(\d+):\s+(.*)\s+\[([A-Za-z0-9_.-]+)]$/;

// Accumulate matches for file write
const matches = [];

const gradle = spawn('.\\gradlew.bat', ['detektAll', '--no-build-cache', '--no-configuration-cache', '--rerun-tasks'], { cwd: PROJECT_ROOT, shell: true });

gradle.stdout.on('data', d => processDetektChunk(d.toString()));
gradle.stderr.on('data', d => processDetektChunk(d.toString()));

gradle.on('close', code => {
  const outDir = resolve(PROJECT_ROOT, 'build', 'reports', 'detekt');
  const outFile = resolve(outDir, 'detekt-issues-for-file.txt');
  try {
    // Ensure directory exists (recursive true is safe if already there)
    require('fs').mkdirSync(outDir, { recursive: true });
    const header = `Detekt findings for ${relTarget} (count=${matches.length})\n`;
    writeFileSync(outFile, header + matches.join('\n') + (matches.length ? '\n' : ''));
    console.error(`Wrote ${matches.length} findings to ${outFile}`);
  } catch (e) {
    console.error('Failed to write output file:', e);
  }
});

function processDetektChunk(chunk) {
  const lines = chunk.split(/\r?\n/);
  for (const l of lines) {
    if (!l.includes('.kt:')) continue;
    let m = l.match(classicRegex);
    let filePath;
    let line;
    let rule;
    let msg;
    if (m) {
      filePath = m[1].replace(/\\/g, '/');
      line = m[2];
      rule = m[4];
      msg = m[5];
    } else {
      const mb = l.match(bracketRegex);
      if (!mb) continue;
      filePath = mb[1].replace(/\\/g, '/');
      line = mb[2];
      rule = mb[5];
      msg = mb[4];
    }
    if (filePath.startsWith(PROJECT_ROOT.replace(/\\/g, '/') + '/')) {
      filePath = filePath.substring(PROJECT_ROOT.replace(/\\/g, '/').length + 1);
    }
    if (filePath === relTarget) {
      const entry = `${filePath}:${line}: ${rule} -> ${msg}`;
      matches.push(entry);
      console.log(entry);
    }
  }
}
