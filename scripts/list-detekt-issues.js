#!/usr/bin/env node
/**
 * Script: list-detekt-issues.js
 * Description:
 *  Runs the detekt aggregate report task, parses its console output to count issues per Kotlin file,
 *  then enumerates relevant .kt files (excluding the .history directory) and produces a report sorted by descending issue count.
 *
 * Output file: build/detekt-issues-report.txt
 *
 * Notes:
 *  - Because detekt XML/HTML artifacts weren't accessible via the current workspace file API after generation,
 *    this script relies on capturing stdout from the Gradle invocation and parsing lines containing paths.
 *  - It recognizes Kotlin file paths in the output and increments a counter for each reported rule violation line.
 *  - Files with zero issues are now intentionally excluded from the report (requested behavior).
 *  - The `.history` directory is ignored entirely.
 */

const { spawn } = require('child_process');
const { readdirSync, statSync, writeFileSync, mkdirSync } = require('fs');
const { join, sep } = require('path');

const PROJECT_ROOT = process.cwd();
const OUTPUT_FILE = join(PROJECT_ROOT, 'build','reports', 'detekt', 'detekt-issues-report.txt');

function listKotlinFiles(dir) {
  const entries = readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const e of entries) {
    if (e.name === 'build') continue; // skip build outputs to avoid noise
    if (e.name === '.history') continue; // skip history directory entirely
    const full = join(dir, e.name);
    if (e.isDirectory()) {
      files.push(...listKotlinFiles(full));
    } else if (e.isFile() && e.name.endsWith('.kt')) {
      files.push(full);
    }
  }
  return files;
}

function normalizePath(p) {
  return p.replace(/\\/g, '/');
}

function runGradle() {
  return new Promise((resolve, reject) => {
    // Use detektAll (aggregate execution) instead of detektAggregateReport since we only need per-file violation lines.
    const args = ['detektAll', '--no-build-cache', '--no-configuration-cache', '--rerun-tasks'];
    const gradle = spawn('.' + sep + 'gradlew.bat', args, { cwd: PROJECT_ROOT, shell: true });
    let stdout = '';
    let stderr = '';

    gradle.stdout.on('data', d => { stdout += d.toString(); });
    gradle.stderr.on('data', d => { stderr += d.toString(); });

    gradle.on('close', code => {
      if (code !== 0) {
        console.error('Gradle detekt task failed with exit code', code);
        // Still attempt to parse what we captured.
      }
      resolve({ stdout, stderr, code });
    });
    gradle.on('error', err => reject(err));
  });
}

function parseDetektOutput(outText) {
  const counts = new Map();
  const kotlinPathRegex = /([A-Z]:\\[^\n\r]+?\.kt)|((?:\.|[A-Za-z0-9_\-\/])+\.kt)/g; // matches Windows or relative style paths ending .kt

  const lines = outText.split(/\r?\n/);
  for (const line of lines) {
    // detekt violation lines usually start with a path or contain one followed by ':' and line number
    if (!line.includes('.kt:')) continue;
    const match = line.match(kotlinPathRegex);
    if (match) {
      // choose the longest match (most specific path)
      const best = match.reduce((a, b) => (b.length > a.length ? b : a), match[0]);
      // Normalize to project-relative path if inside project
      let rel = best;
      const norm = normalizePath(best);
      const normRoot = normalizePath(PROJECT_ROOT) + '/';
      if (norm.startsWith(normRoot)) {
        rel = norm.substring(normRoot.length);
      } else {
        rel = norm; // leave as-is if outside (shouldn't happen normally)
      }
      counts.set(rel, (counts.get(rel) || 0) + 1);
    }
  }
  return counts;
}

(async function main() {
  console.log('Running detekt aggregate report...');
  const { stdout, stderr } = await runGradle();
  const issueCounts = parseDetektOutput(stdout + '\n' + stderr);

  console.log('Scanning Kotlin files...');
  // Build rows only for files that actually have issues (>0) and are not in .history
  const rootNorm = normalizePath(PROJECT_ROOT) + '/';
  const rows = Array.from(issueCounts.entries())
    .map(([file, count]) => ({ file, count }))
    .filter(r => r.count > 0 && !r.file.startsWith('.history/'))
    .sort((a, b) => b.count - a.count || a.file.localeCompare(b.file));

  const header = 'Detekt Issues per Kotlin File (>0 only, sorted desc by issue count)';
  const bodyLines = rows.map(r => `${r.count.toString().padStart(4, ' ')}  ${r.file}`);
  const totalFindings = rows.reduce((sum, r) => sum + r.count, 0);
  const summaryLine = `Total findings: ${totalFindings}`;
  const report = [header, ''.padEnd(header.length, '='), ...bodyLines, '', summaryLine].join('\n');

  // Ensure output directory exists recursively
  try {
    mkdirSync(join(PROJECT_ROOT, 'build', 'reports', 'detekt'), { recursive: true });
  } catch (e) {
    console.error('Failed to create detekt report directory', e);
  }
  writeFileSync(OUTPUT_FILE, report, 'utf8');
  console.log('Report written to', OUTPUT_FILE);
  console.log('Total findings:', totalFindings);
})();
