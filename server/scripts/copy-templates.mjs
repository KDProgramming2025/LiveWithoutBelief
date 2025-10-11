import { cpSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const src = path.resolve(__dirname, '../src/templates')
const dest = path.resolve(__dirname, '../dist/templates')
const scriptsSrc = path.resolve(__dirname)
const scriptsDest = path.resolve(__dirname, '../dist/scripts')

if (existsSync(src)) {
  cpSync(src, dest, { recursive: true })
  console.log(`[postbuild] Copied templates to ${dest}`)
} else {
  console.warn('[postbuild] No templates directory found at', src)
}

// Also copy maintenance scripts (this directory) to dist/scripts
try {
  cpSync(scriptsSrc, scriptsDest, { recursive: true })
  console.log(`[postbuild] Copied maintenance scripts to ${scriptsDest}`)
} catch (e) {
  console.warn('[postbuild] Failed to copy maintenance scripts:', e?.message || e)
}
