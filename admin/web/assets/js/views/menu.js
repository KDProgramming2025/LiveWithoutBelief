import { api } from '../core/api.js'
import { state } from '../core/state.js'

export async function viewMenu(){
  const el = document.createElement('div')
  el.innerHTML = `
      <section class="card">
        <h2>Create Menu Item</h2>
        <form id="menu-form" class="menu-form" autocomplete="off">
          <div class="mf-grid">
            <div class="mf-field">
              <label for="mf-title">Title</label>
              <input id="mf-title" class="input" name="title" placeholder="Title" required />
              <small class="hint">Displayed name of the menu item</small>
            </div>
            <div class="mf-field">
              <label for="mf-label">Label</label>
              <input id="mf-label" class="input" name="label" placeholder="Label (optional)" />
              <small class="hint">Optional subtitle or description</small>
            </div>
            <div class="mf-field">
              <label for="mf-order">Order</label>
              <input id="mf-order" class="input" name="order" type="number" placeholder="#" min="0" />
              <small class="hint">Position in the list (0-n)</small>
            </div>
            <div class="mf-field mf-icon">
              <label>Icon</label>
              <div class="mf-dropzone" id="menu-icon-drop" role="button" tabindex="0" aria-label="Upload icon">
                <input class="mc-file" id="menu-icon-input" name="icon" type="file" accept="image/*" />
                <div class="dz-body">
                  <span class="dz-graphic" aria-hidden="true">🖼️</span>
                  <div class="dz-text">
                    <strong>Drop image</strong> or click to upload
                    <div class="dz-sub">PNG, JPG, SVG up to ~2MB</div>
                  </div>
                </div>
              </div>
              <div class="mc-thumb" id="menu-icon-thumb" hidden></div>
            </div>
          </div>
          <div class="mf-actions">
            <button class="button" type="submit">
              <span class="nav-item__icon" aria-hidden="true" data-lucide="plus"></span>
              Add Item
            </button>
            <span id="menu-uploading" class="loader-badge" hidden>
              <span class="loader-dots" aria-hidden="true">
                <span></span><span></span><span></span>
              </span>
              <span class="loader-text">Uploading</span>
            </span>
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
  const iconDrop = el.querySelector('#menu-icon-drop')
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
  grid.addEventListener('change', async (e) => {
    const input = e.target.closest('input[type=file][data-edit-icon]')
    if(!input) return
    const id = input.getAttribute('data-edit-icon')
    const iconBox = input.closest('.menu-card').querySelector('.menu-card__icon .uploading')
    const fd = new FormData()
    if(input.files && input.files[0]) fd.append('icon', input.files[0])
    const headers = {}
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`
    iconBox.style.display = 'inline-block'
    try{
      const res = await fetch(`/v1/admin/menu/${id}/icon`, { method: 'POST', body: fd, headers })
      if(res.status === 204){ await loadMenu() }
    } finally {
      iconBox.style.display = 'none'
    }
  })
  form.addEventListener('submit', async (e) => {
    e.preventDefault()
    const fd = new FormData(form)
    const headers = {}
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`
  uploading.hidden = false
    try{
      const res = await fetch('/v1/admin/menu', { method: 'POST', body: fd, headers })
      if(res.ok){
        form.reset();
        if(iconThumb){ iconThumb.innerHTML=''; iconThumb.hidden = true }
        await loadMenu()
      }
    } finally {
  uploading.hidden = true
    }
  })
  // Drag & drop and click-to-upload behaviors for icon input
  if(iconDrop && iconInput){
    const openPicker = () => iconInput.click()
    iconDrop.addEventListener('click', openPicker)
    iconDrop.addEventListener('keydown', (ev) => { if(ev.key === 'Enter' || ev.key === ' '){ ev.preventDefault(); openPicker() } })
    ;['dragenter','dragover'].forEach(evt => iconDrop.addEventListener(evt, (ev) => { ev.preventDefault(); ev.stopPropagation(); iconDrop.classList.add('is-dragover') }))
    ;['dragleave','dragend','drop'].forEach(evt => iconDrop.addEventListener(evt, (ev) => { iconDrop.classList.remove('is-dragover') }))
    iconDrop.addEventListener('drop', (ev) => {
      ev.preventDefault(); ev.stopPropagation();
      const file = ev.dataTransfer?.files?.[0]
      if(!file) return
      try{
        const dt = new DataTransfer()
        dt.items.add(file)
        iconInput.files = dt.files
        iconInput.dispatchEvent(new Event('change', { bubbles:true }))
      }catch{
        // Fallback: show preview without binding to input
        const url = URL.createObjectURL(file)
        if(iconThumb){
          iconThumb.innerHTML = `<img src="${url}" alt="preview" /> <button type="button" class="mc-clear" aria-label="Clear icon">×</button>`
          iconThumb.hidden = false
          const clearBtn = iconThumb.querySelector('.mc-clear')
          clearBtn?.addEventListener('click', () => { iconThumb.innerHTML=''; iconThumb.hidden=true })
        }
      }
    })
  }
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
