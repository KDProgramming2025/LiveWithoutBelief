import { cpSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const src = path.resolve(__dirname, '../src/templates')
const dest = path.resolve(__dirname, '../dist/templates')

if (existsSync(src)) {
  cpSync(src, dest, { recursive: true })
  console.log(`[postbuild] Copied templates to ${dest}`)
} else {
  console.warn('[postbuild] No templates directory found at', src)
}
