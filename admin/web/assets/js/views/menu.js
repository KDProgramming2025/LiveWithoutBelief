import { api } from '../core/api.js'
import { state } from '../core/state.js'

export async function viewMenu(){
  const el = document.createElement('div')
  el.innerHTML = `
      <section class="card">
        <form id="menu-form" class="menu-compact" autocomplete="off">
          <div class="mc-row">
            <input class="input" name="title" placeholder="Title" required />
            <input class="input" name="label" placeholder="Label" />
            <input class="input" name="order" type="number" placeholder="#" min="0" />
            <label class="mc-upload button secondary">
              Icon<input class="mc-file" name="icon" type="file" accept="image/*" />
            </label>
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
    uploading.style.display = 'inline-block'
    try{
      const res = await fetch('/v1/admin/menu', { method: 'POST', body: fd, headers })
      if(res.ok){ form.reset(); await loadMenu() }
    } finally {
      uploading.style.display = 'none'
    }
  })
  await loadMenu()
  return el
}
