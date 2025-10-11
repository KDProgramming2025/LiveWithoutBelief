import fs from 'node:fs/promises'
import fssync from 'node:fs'
import path from 'node:path'

// Refresh per-article styles.css and script.js from latest templates and add cache-busting in index.html
// Defaults align with ArticleService paths
const baseDir = '/var/www/LWB/admin'
const articlesDir = path.join(baseDir, 'web', 'articles')
const manifestPath = path.join(baseDir, 'articles.json')
const serverRoot = '/var/www/LWB/server'
const templatesDir = path.join(serverRoot, 'dist', 'templates', 'articles')

async function main() {
  const ts = Date.now().toString()
  const cssPath = path.join(templatesDir, 'styles.css')
  const jsPath = path.join(templatesDir, 'script.js')
  if (!fssync.existsSync(cssPath) || !fssync.existsSync(jsPath)) {
    console.error('Templates not found. Build the server to generate dist/templates first.')
    process.exit(2)
  }
  const [css, js] = await Promise.all([
    fs.readFile(cssPath, 'utf8'),
    fs.readFile(jsPath, 'utf8'),
  ])
  if (!fssync.existsSync(manifestPath)) {
    console.error('Manifest not found:', manifestPath)
    process.exit(1)
  }
  const raw = await fs.readFile(manifestPath, 'utf8')
  let items
  try { items = JSON.parse(raw) } catch { items = [] }
  const slugs = items.map(it => it.slug).filter(Boolean)
  let updated = 0
  for (const slug of slugs) {
    const dir = path.join(articlesDir, slug)
    const idx = path.join(dir, 'index.html')
    if (!fssync.existsSync(dir)) continue
    try {
      // Overwrite assets
      await fs.writeFile(path.join(dir, 'styles.css'), css, 'utf8')
      await fs.writeFile(path.join(dir, 'script.js'), js, 'utf8')
      // Add/update cache-busting in index.html
      if (fssync.existsSync(idx)) {
        let html = await fs.readFile(idx, 'utf8')
        // Normalize and replace existing versioned links too
        html = html
          .replace(/href="\.\/styles\.css(\?v=[^\"]*)?"/g, `href="./styles.css?v=${ts}"`)
          .replace(/src="\.\/script\.js(\?v=[^\"]*)?"/g, `src="./script.js?v=${ts}"`)
        await fs.writeFile(idx, html, 'utf8')
      }
      updated++
    } catch (e) {
      console.error('Failed to refresh', slug, e?.message || e)
    }
  }
  console.log('Refreshed articles:', updated)
}

main().catch(err => { console.error(err); process.exit(1) })
