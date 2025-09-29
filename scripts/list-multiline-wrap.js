#!/usr/bin/env node
/**
 * Utility: list all detekt.ktlint.MultilineExpressionWrapping findings with file:line.
 * Scans all build/**/detekt/*.sarif files.
 */
const fs = require('fs');
const path = require('path');

function findSarifFiles(dir) {
  const out = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === 'node_modules' || e.name.startsWith('.git')) continue;
      out.push(...findSarifFiles(p));
    } else if (e.isFile() && e.name.endsWith('.sarif') && p.includes(path.join('build', 'reports', 'detekt'))) {
      out.push(p);
    }
  }
  return out;
}

function main() {
  const root = process.cwd();
  const sarifFiles = findSarifFiles(root);
  if (sarifFiles.length === 0) {
    console.error('No SARIF files found');
    process.exit(1);
  }
  const findings = [];
  for (const file of sarifFiles) {
    let json;
    try {
      const text = fs.readFileSync(file, 'utf8');
      json = JSON.parse(text);
    } catch (e) {
      console.error('Failed parsing', file, e.message);
      continue;
    }
    const runs = json.runs || [];
    for (const run of runs) {
      for (const res of run.results || []) {
        if (res.ruleId === 'detekt.ktlint.MultilineExpressionWrapping') {
          const loc = (res.locations && res.locations[0]) || {};
            const phys = loc.physicalLocation || {};
            const art = phys.artifactLocation || {};
            const region = phys.region || {};
            const uri = art.uri || 'UNKNOWN';
            const line = region.startLine || 0;
            findings.push({ uri, line });
        }
      }
    }
  }
  findings.sort((a, b) => a.uri === b.uri ? a.line - b.line : a.uri.localeCompare(b.uri));
  // Aggregate counts per file
  const perFile = findings.reduce((m, f) => { m[f.uri] = (m[f.uri] || 0) + 1; return m; }, {});
  const total = findings.length;
  console.log(`# MultilineExpressionWrapping findings: ${total}`);
  console.log('\n## Top files by count');
  Object.entries(perFile).sort((a,b)=> b[1]-a[1]).forEach(([file, count]) => {
    console.log(`${count.toString().padStart(4)}  ${file}`);
  });
  console.log('\n## Detailed list (file:line)');
  findings.forEach(f => console.log(`${f.uri}:${f.line}`));
}

main();
