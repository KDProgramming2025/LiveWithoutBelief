import { api } from '../core/api.js'
import { state } from '../core/state.js'
import { fmtBytes, fmtEta } from '../core/helpers.js'

export async function viewMenu(){
  const el = document.createElement('div')
  el.innerHTML = `
      <section class="card">
        <form id="menu-form" class="menu-compact" autocomplete="off">
          <div class="mc-row">
            <input class="input" name="title" placeholder="Title" required />
            <input class="input" name="label" placeholder="Label" />
            <input class="input" name="order" type="number" placeholder="#" min="0" />
            <div class="mc-icon-slot">
              <label class="mc-upload button secondary">
                Icon<input class="mc-file" id="menu-icon-input" name="icon" type="file" accept="image/*" />
              </label>
              <div class="mc-thumb" id="menu-icon-thumb" hidden></div>
            </div>
            <button class="button mc-add" type="submit">Add</button>
            <span id="menu-uploading" class="badge" style="display:none">Uploading…</span>
          </div>
        </form>
      </section>
      <section class="card">
        <h2>Menu Items</h2>
        <div id="menu-grid" class="grid"></div>
      </section>`
  const grid = el.querySelector('#menu-grid')
  const form = el.querySelector('#menu-form')
  const uploading = el.querySelector('#menu-uploading')
  const iconInput = el.querySelector('#menu-icon-input')
  const iconThumb = el.querySelector('#menu-icon-thumb')
  function iconUrl(m){ return m.iconPath ? `/LWB/Admin/uploads${m.iconPath.replace('/uploads','')}` : '' }
  function renderCard(m){
    const card = document.createElement('div')
    card.className = 'menu-card'
    card.innerHTML = `
        <div class="menu-card__icon">${m.iconPath ? `<img src="${iconUrl(m)}" alt="icon"/>` : '<div class="placeholder">No Icon</div>'}<span class="uploading" style="display:none">Uploading…</span></div>
        <div class="menu-card__body">
          <div class="menu-card__title">${m.title}</div>
          <div class="menu-card__label">${m.label ?? ''}</div>
        </div>
        <div class="menu-card__actions">
          <button class="button secondary" data-move="up" data-id="${m.id}">↑</button>
            <button class="button secondary" data-move="down" data-id="${m.id}">↓</button>
            <button class="button secondary" data-edit="item" data-id="${m.id}" data-title="${m.title ?? ''}" data-label="${m.label ?? ''}">Edit</button>
            <label class="button secondary" style="position:relative;overflow:hidden">Edit Icon<input type="file" accept="image/*" data-edit-icon="${m.id}" style="position:absolute;inset:0;opacity:0;cursor:pointer"></label>
            <button class="button secondary" data-del="${m.id}">Delete</button>
        </div>`
    return card
  }
  async function loadMenu(){
    const res = await api('/menu')
    if(res.status === 401) throw new Error('unauthorized')
    const json = await res.json()
    grid.innerHTML = ''
    for(const m of json.items){ grid.appendChild(renderCard(m)) }
  }
  grid.addEventListener('click', async (e) => {
    const del = e.target.closest('button[data-del]')
    if(del){
      if(!confirm('Delete this menu item?')) return
      const id = del.getAttribute('data-del')
      const res = await api(`/menu/${id}`, { method:'DELETE' })
      if(res.status === 204){ await loadMenu() }
      return
    }
    const mv = e.target.closest('button[data-move]')
    if(mv){
      const id = mv.getAttribute('data-id')
      const direction = mv.getAttribute('data-move')
      const headers = { 'Content-Type': 'application/json' }
      if (state.token) headers['Authorization'] = `Bearer ${state.token}`
      const res = await fetch(`/v1/admin/menu/${id}/move`, { method: 'POST', headers, body: JSON.stringify({ direction }) })
      if(res.status === 204){ await loadMenu() }
      return
    }
    const edt = e.target.closest('button[data-edit="item"]')
    if(edt){
      const id = edt.getAttribute('data-id')
      const currentTitle = edt.getAttribute('data-title') || ''
      const currentLabel = edt.getAttribute('data-label') || ''
      const overlay = document.getElementById('modal-overlay')
      const closeBtn = document.getElementById('modal-close')
      const modalContent = document.getElementById('modal-content')
      modalContent.innerHTML = `
          <h3>Edit Menu Item</h3>
          <form id="edit-item-form" class="form-grid">
            <input class="input" name="title" placeholder="Title" value="${currentTitle.replaceAll('"','&quot;')}" required />
            <input class="input" name="label" placeholder="Label" value="${currentLabel.replaceAll('"','&quot;')}" />
            <div class="row"><button class="button" type="submit">Save</button></div>
          </form>`
      overlay.hidden = false
      overlay.className = 'overlay'
      function hide(){ overlay.hidden = true; overlay.className = '' }
      closeBtn.onclick = hide
      const form = document.getElementById('edit-item-form')
      form.onsubmit = async (ev) => {
        ev.preventDefault()
        const fd = new FormData(form)
        const payload = { title: fd.get('title'), label: fd.get('label') }
        const headers = { 'Content-Type': 'application/json' }
        if (state.token) headers['Authorization'] = `Bearer ${state.token}`
        const res = await fetch(`/v1/admin/menu/${id}`, { method: 'PATCH', headers, body: JSON.stringify(payload) })
        if(res.ok){ hide(); await loadMenu() }
      }
      return
    }
  })
  function uploadWithProgress({ url, method='POST', formData, onProgress }){
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open(method, url)
      if(state.token) xhr.setRequestHeader('Authorization', `Bearer ${state.token}`)
      const start = performance.now()
      let lastLoaded = 0
      let lastTime = start
      xhr.upload.onprogress = (ev) => {
        if(ev.lengthComputable){
          const now = performance.now()
          const deltaBytes = ev.loaded - lastLoaded
            const deltaTime = (now - lastTime)/1000
            const speed = deltaTime > 0 ? deltaBytes / deltaTime : 0
            lastLoaded = ev.loaded; lastTime = now
            const remaining = ev.total - ev.loaded
            const eta = speed > 0 ? remaining / speed : 0
            onProgress?.({ loaded: ev.loaded, total: ev.total, percent: ev.loaded/ev.total*100, speed, eta })
        }
      }
      xhr.onreadystatechange = () => {
        if(xhr.readyState === 4){
          if(xhr.status >=200 && xhr.status <300) resolve(xhr)
          else reject(new Error('upload failed'))
        }
      }
      xhr.onerror = () => reject(new Error('network error'))
      xhr.send(formData)
    })
  }

  grid.addEventListener('change', async (e) => {
    const input = e.target.closest('input[type=file][data-edit-icon]')
    if(!input) return
    const id = input.getAttribute('data-edit-icon')
    const iconWrapper = input.closest('.menu-card').querySelector('.menu-card__icon')
    const status = iconWrapper.querySelector('.uploading')
    status.style.display = 'inline-block'
    status.textContent = '0%'
    const fd = new FormData()
    if(input.files && input.files[0]) fd.append('icon', input.files[0])
    try{
      await uploadWithProgress({
        url: `/v1/admin/menu/${id}/icon`,
        formData: fd,
        onProgress: ({ percent, speed, eta }) => {
          status.textContent = `${percent.toFixed(0)}% ${fmtBytes(speed)}/s ETA ${fmtEta(eta)}`
        }
      })
      await loadMenu()
    } catch(_e){
      status.textContent = 'Error'
      setTimeout(()=>{ status.style.display='none' }, 1500)
    } finally {
      setTimeout(()=>{ status.style.display='none' }, 400)
    }
  })
  form.addEventListener('submit', async (e) => {
    e.preventDefault()
    const fd = new FormData(form)
    uploading.style.display = 'inline-block'
    uploading.textContent = '0%'
    try {
      await uploadWithProgress({
        url:'/v1/admin/menu',
        formData: fd,
        onProgress: ({ percent, speed, eta }) => {
          uploading.textContent = `${percent.toFixed(0)}% ${fmtBytes(speed)}/s ETA ${fmtEta(eta)}`
        }
      })
      form.reset()
      if(iconThumb){ iconThumb.innerHTML=''; iconThumb.hidden = true }
      await loadMenu()
      uploading.textContent = 'Done'
      setTimeout(()=> uploading.style.display='none', 800)
    } catch(_e){
      uploading.textContent = 'Error'
      setTimeout(()=> uploading.style.display='none', 1200)
    }
  })
  iconInput?.addEventListener('change', () => {
    if(!iconInput.files || !iconInput.files[0]){ iconThumb.hidden = true; iconThumb.innerHTML=''; return }
    const file = iconInput.files[0]
    const url = URL.createObjectURL(file)
    iconThumb.innerHTML = `<img src="${url}" alt="preview" /> <button type="button" class="mc-clear" aria-label="Clear icon">×</button>`
    iconThumb.hidden = false
    const clearBtn = iconThumb.querySelector('.mc-clear')
    clearBtn.addEventListener('click', () => {
      iconInput.value = ''
      iconThumb.innerHTML = ''
      iconThumb.hidden = true
    })
  })
  await loadMenu()
  return el
}
