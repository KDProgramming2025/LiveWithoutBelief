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
          <input class="input" name="label" placeholder="Label" required />
          <input class="input" name="order" type="number" placeholder="Order (0..n)" min="0" />
          <div class="row">
            <label class="button secondary" style="position:relative;overflow:hidden">Cover Image<input class="input" name="cover" type="file" accept="image/*" style="position:absolute;inset:0;opacity:0;cursor:pointer" /></label>
            <label class="button secondary" style="position:relative;overflow:hidden">Icon<input class="input" name="icon" type="file" accept="image/*" style="position:absolute;inset:0;opacity:0;cursor:pointer" /></label>
            <label class="button" style="position:relative;overflow:hidden">DOCX<input class="input" name="docx" type="file" accept=".docx" required style="position:absolute;inset:0;opacity:0;cursor:pointer" /></label>
          </div>
          <div class="row">
            <button class="button" id="article-submit" type="submit">Upload</button>
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
  const uploading = el.querySelector('#article-uploading')
  const fetchList = async () => {
    const res = await api('/articles')
    const json = await res.json()
    listEl.innerHTML = ''
    for(const a of json.items){
      const card = document.createElement('div')
      card.className = 'menu-card'
      card.innerHTML = `
          <div class="menu-card__icon">${a.iconUrl ? `<img src="${a.iconUrl}" alt="icon"/>` : '<div class="placeholder">No Icon</div>'}</div>
          <div class="menu-card__body">
            <div class="menu-card__title">${a.title}</div>
            <div class="menu-card__label">${a.label ?? ''}</div>
          </div>
          <div class="menu-card__actions">
            <a class="button secondary" href="${a.indexUrl}" target="_blank" rel="noopener">Open</a>
            <button class="button secondary" data-article-del="${a.id}">Delete</button>
          </div>`
      listEl.appendChild(card)
    }
  }
  listEl.addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-article-del]')
    if(!btn) return
    const id = btn.getAttribute('data-article-del')
    const ok = await modalConfirm('Delete this article? This will remove the DOCX, the generated folder, and the manifest entry.', { danger: true, confirmText: 'Delete' })
    if(!ok) return
    const headers = {}
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`
    const res = await fetch(`/v1/admin/articles/${encodeURIComponent(id)}`, { method:'DELETE', headers })
    if(res.status === 204){ await fetchList() }
    else if(res.status === 401){ clearToken(); document.getElementById('login-overlay').hidden=false; document.getElementById('login-overlay').className='overlay' }
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
  await fetchList()
  return el
}
