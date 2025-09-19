import { api } from '../core/api.js'
import { state, clearToken } from '../core/state.js'
import { fmtBytes, fmtEta } from '../core/helpers.js'
import { confirm as modalConfirm } from '../ui/modal.js'

export async function viewArticles(){
  const el = document.createElement('div')
  el.innerHTML = `
      <section class="card">
        <h3>Upload Article (.docx)</h3>
        <form id="article-form" class="form-grid">
          <input class="input" name="title" placeholder="Title" required />
          <div class="row row--two">
            <input class="input" name="label" placeholder="Label" required />
            <input class="input" name="order" type="number" placeholder="Order (0..n)" min="0" />
          </div>
          <div class="row row--center file-tiles" role="group" aria-label="Article files">
            <div class="file-tile-wrap">
              <label class="file-tile file-button" data-kind="icon" aria-label="Icon image">
                <span class="tile-icon" data-lucide="image"></span>
                <input class="input" name="icon" type="file" accept="image/*" aria-label="Select icon image" />
              </label>
              <div class="tile-caption">Icon</div>
            </div>
            <div class="file-tile-wrap">
              <label class="file-tile file-button" data-kind="cover" aria-label="Cover image">
                <span class="tile-icon" data-lucide="image"></span>
                <input class="input" name="cover" type="file" accept="image/*" aria-label="Select cover image" />
              </label>
              <div class="tile-caption">Cover</div>
            </div>
            <div class="file-tile-wrap">
              <label class="file-tile file-button" data-kind="docx" aria-label="Article DOCX">
                <span class="tile-icon" data-lucide="file-text"></span>
                <input class="input" name="docx" type="file" accept=".docx" required aria-label="Select DOCX file" />
              </label>
              <div class="tile-caption">DOCX</div>
            </div>
          </div>
          <button class="button button--block" id="article-submit" type="submit">Upload</button>
          <div class="row row--center">
            <span id="article-uploading" class="badge" style="display:none">Uploading…</span>
          </div>
        </form>
        <div id="article-progress" style="display:none;gap:8px;align-items:center">
          <div class="progress" style="flex:1"><div class="progress__bar" id="article-progress-bar"></div></div>
          <div class="progress__text" id="article-progress-text">0%</div>
          <button class="button secondary" id="article-cancel" type="button" style="display:none">Cancel</button>
        </div>
      </section>
      <section class="card" style="margin-top: var(--space-16)">
        <h2>Articles</h2>
        <div id="article-list" class="grid"></div>
      </section>`
  const listEl = el.querySelector('#article-list')
  const form = el.querySelector('#article-form')
  const fileTiles = el.querySelector('.file-tiles')
  const uploading = el.querySelector('#article-uploading')
  const fetchList = async () => {
  const res = await api('/articles')
    const json = await res.json()
    listEl.innerHTML = ''
    let items = json.items.slice()
    for(const a of items){
      const card = document.createElement('div')
      card.className = 'menu-card article-card'
      card.innerHTML = `
          ${a.coverUrl ? `<div class="article-card__cover"><img src="${a.coverUrl}" alt="cover"/></div>` : ''}
          <div class="article-card__meta">
            <div class="menu-card__icon">${a.iconUrl ? `<img src="${a.iconUrl}" alt="icon"/>` : '<div class="placeholder">—</div>'}</div>
            <div class="article-card__title" title="${(a.title||'').replaceAll('"','&quot;')}">${a.title}</div>
          </div>
          <div class="article-card__footer">
            <div class="article-card__label">${a.label ?? ''}</div>
            <div class="menu-card__actions actions-equal">
              <a class="button secondary" href="${a.indexUrl}" target="_blank" rel="noopener">Open</a>
              <button class="button danger" data-article-del="${a.id}">Delete</button>
            </div>
          </div>
          <div class="article-card__move">
            <div class="menu-card__move">
              <button class="button secondary btn-move btn-move--up" data-article-move="up" data-id="${a.id}">↑</button>
              <button class="button secondary btn-move btn-move--down" data-article-move="down" data-id="${a.id}">↓</button>
            </div>
          </div>`
      listEl.appendChild(card)
    }
  }
  listEl.addEventListener('click', async (e) => {
    // Move up/down with optimistic local reorder (fallback when API isn't available)
    const mv = e.target.closest('button[data-article-move]')
    if(mv){
      const id = mv.getAttribute('data-id')
      const dir = mv.getAttribute('data-article-move')
      try{
        // Try API first if implemented later
        const headers = { 'Content-Type': 'application/json' }
        if (state.token) headers['Authorization'] = `Bearer ${state.token}`
        const res = await fetch(`/v1/admin/articles/${encodeURIComponent(id)}/move`, { method:'POST', headers, body: JSON.stringify({ direction: dir }) })
        if(res.status === 204){ await fetchList(); return }
      }catch{}
      // Fallback: local reorder in DOM
      const cards = Array.from(listEl.children)
      const idx = cards.findIndex(c => c.querySelector(`button[data-id="${CSS.escape(id||'')}"]`))
      if(idx >= 0){
        if(dir === 'up' && idx > 0){ listEl.insertBefore(cards[idx], cards[idx-1]) }
        if(dir === 'down' && idx < cards.length - 1){ listEl.insertBefore(cards[idx+1], cards[idx]) }
      }
      return
    }
    const btn = e.target.closest('button[data-article-del]')
    if(!btn) return
    const id = btn.getAttribute('data-article-del')
    const ok = await modalConfirm('Delete this article? This will remove the DOCX, the generated folder, and the manifest entry.', { danger: true, confirmText: 'Delete' })
    if(!ok) return
    const headers = {}
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`
    const res = await fetch(`/v1/admin/articles/${encodeURIComponent(id)}`, { method:'DELETE', headers })
    if(res.status === 204){ await fetchList() }
  else if(res.status === 401){ /* handled centrally by api() */ }
    else {
      await modalConfirm('Delete failed', { confirmText: 'Close' })
    }
  })
  form.addEventListener('submit', async (e) => {
    e.preventDefault()
    const fd = new FormData(form)
    const title = String(fd.get('title') || '').trim()
    const current = Array.from(listEl.querySelectorAll('.menu-card .menu-card__title')).map(n => n.textContent?.trim().toLowerCase())
    if(current.includes(title.toLowerCase())){
      const ok = await modalConfirm('An article with the same title exists. Uploading will replace it. Continue?', { confirmText: 'Continue' })
      if(!ok) return
    }
    const xhr = new XMLHttpRequest()
    const progWrap = el.querySelector('#article-progress')
    const progBar = el.querySelector('#article-progress-bar')
    const progText = el.querySelector('#article-progress-text')
    const cancelBtn = el.querySelector('#article-cancel')
    const submitBtn = el.querySelector('#article-submit')
    progWrap.style.display = 'flex'
    progBar.style.width = '0%'
    progText.textContent = '0%'
    uploading.style.display = 'inline-block'
    submitBtn.disabled = true
    cancelBtn.style.display = 'inline-block'
    const startedAt = Date.now()
    let lastLoaded = 0
    let lastAt = startedAt
    xhr.open('POST', '/v1/admin/articles')
    if (state.token) xhr.setRequestHeader('Authorization', `Bearer ${state.token}`)
    xhr.upload.onprogress = (ev) => {
      if(!ev.lengthComputable) return
      const percent = Math.min(100, Math.round((ev.loaded / ev.total) * 100))
      progBar.style.width = percent + '%'
      const now = Date.now()
      const dt = Math.max(1, now - lastAt) / 1000
      const dbytes = Math.max(0, ev.loaded - lastLoaded)
      const speed = dbytes / dt
      const remain = Math.max(0, ev.total - ev.loaded)
      const etaSec = speed > 0 ? Math.round(remain / speed) : 0
      progText.textContent = `${percent}% • ${fmtBytes(ev.loaded)} / ${fmtBytes(ev.total)} • ${fmtBytes(speed)}/s • ${fmtEta(etaSec)}`
      lastLoaded = ev.loaded
      lastAt = now
    }
    xhr.onreadystatechange = async () => {
      if(xhr.readyState !== 4) return
      uploading.style.display = 'none'
      progWrap.style.display = 'none'
      submitBtn.disabled = false
      cancelBtn.style.display = 'none'
      if(xhr.status >= 200 && xhr.status < 300){
        form.reset(); await fetchList()
      } else if (xhr.status === 401) {
        clearToken(); document.getElementById('login-overlay').hidden = false; document.getElementById('login-overlay').className = 'overlay'
      } else {
        await modalConfirm('Upload failed: ' + (xhr.statusText || 'Unknown error'), { confirmText: 'Close' })
      }
    }
    cancelBtn.onclick = () => { try{ xhr.abort() }catch{} }
    xhr.send(fd)
  })
  // Reset tiles to default icons on form reset
  form.addEventListener('reset', () => {
    for(const tile of fileTiles.querySelectorAll('.file-tile')){
      tile.style.backgroundImage = ''
      const icon = tile.querySelector('.tile-icon, svg.lucide')
      if(icon) icon.style.visibility = 'visible'
      const nameEl = tile.querySelector('.tile-filename')
      if(nameEl) nameEl.remove()
    }
  })
  // Preview selected files inside tiles (images get preview; docx shows filename)
  fileTiles.addEventListener('change', (e) => {
    const input = e.target.closest('input[type=file]')
    if(!input) return
    const tile = input.closest('.file-tile')
    if(!tile) return
    const icon = tile.querySelector('.tile-icon, svg.lucide')
    const kind = tile.getAttribute('data-kind')

    // Reset visuals
    tile.style.backgroundImage = ''
    if(icon) icon.style.visibility = 'visible'
    // Remove any previous filename elements
    const oldName = tile.querySelector('.tile-filename')
    if(oldName) oldName.remove()

    const file = input.files && input.files[0]
    if(!file) return

    if(kind !== 'docx' && input.accept?.includes('image')){
      // Image preview for cover/icon
      const url = URL.createObjectURL(file)
      tile.style.backgroundImage = `url('${url}')`
      tile.style.backgroundSize = 'cover'
      tile.style.backgroundPosition = 'center'
      if(icon) icon.style.visibility = 'hidden'
    } else if (kind === 'docx') {
      // Show filename for docx
      const nameEl = document.createElement('div')
      nameEl.className = 'tile-filename'
      nameEl.title = file.name
      nameEl.textContent = file.name
      tile.appendChild(nameEl)
      // Keep the file-text icon visible behind the text for context
      if(icon) icon.style.visibility = 'visible'
    }
  })
  await fetchList()
  return el
}
