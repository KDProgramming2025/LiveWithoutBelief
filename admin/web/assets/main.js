// Minimal router for admin single-page feel without frameworks
const state = { token: null, view: 'menu' }

function saveToken(t){ localStorage.setItem('lwb_admin_token', t); state.token = t }
function loadToken(){ state.token = localStorage.getItem('lwb_admin_token') }
function clearToken(){ localStorage.removeItem('lwb_admin_token'); state.token = null }

async function api(path, opts={}){
  const headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {})
  if (state.token) headers['Authorization'] = `Bearer ${state.token}`
  const res = await fetch(`/v1/admin${path}`, { ...opts, headers })
  if (res.status === 401) throw new Error('unauthorized')
  return res
}

const views = {
  menu: async () => {
    const el = document.createElement('div')
    el.innerHTML = `
      <section class="card">
        <h3>Add Menu Item</h3>
        <form id="menu-form" class="form-grid">
          <input class="input" name="title" placeholder="Title" required />
          <input class="input" name="label" placeholder="Label" />
          <input class="input" name="order" type="number" placeholder="Order" min="0" />
          <input class="input" name="icon" type="file" accept="image/*" />
          <div class="row">
            <button class="button" type="submit">Add</button>
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
  },
  articles: async () => {
    const el = document.createElement('div')
    el.innerHTML = `
      <section class="card">
        <h2>Articles</h2>
        <div id="article-list"></div>
      </section>
      <section class="card">
        <h3>Upload Article (.docx)</h3>
        <form id="article-form" class="form-grid">
          <input class="input" name="title" placeholder="Title" required />
          <label>Cover Image <input class="input" name="cover" type="file" accept="image/*" /></label>
          <label>Icon <input class="input" name="icon" type="file" accept="image/*" /></label>
          <label>DOCX <input class="input" name="docx" type="file" accept=".docx" required /></label>
          <button class="button" type="submit">Upload</button>
        </form>
      </section>`
    return el
  },
  users: async () => {
    const el = document.createElement('div')
    el.innerHTML = `
      <section class="card">
        <h2>Users</h2>
        <div class="row">
          <input class="input" id="user-q" placeholder="Search by username or email" />
          <button class="button secondary" id="user-search">Search</button>
        </div>
        <div id="user-stats" class="badge" style="margin-top:12px"></div>
        <table class="table" id="user-table">
          <thead><tr><th>User</th><th>Registered</th><th>Bookmarks</th><th>Threads</th><th>Last Login</th><th></th></tr></thead>
          <tbody></tbody>
        </table>
      </section>`
    const stats = el.querySelector('#user-stats')
    const tbody = el.querySelector('#user-table tbody')
    const qEl = el.querySelector('#user-q')
    const load = async () => {
      try{
        const q = qEl.value.trim()
        const res = await api(`/users?limit=50&offset=0${q ? `&q=${encodeURIComponent(q)}`:''}`)
        if(res.status === 401) throw new Error('unauthorized')
        if(!res.ok) throw new Error('failed')
        const json = await res.json()
        stats.textContent = `Total users: ${json.total}`
        tbody.innerHTML = ''
        for(const u of (json.items || [])){
          const tr = document.createElement('tr')
          tr.innerHTML = `
            <td>${u.username ?? '(no-username)'} <span class="badge">#${u.id}</span></td>
            <td>${u.registeredAt?.replace('T',' ').replace('Z','')}</td>
            <td>${u.bookmarks}</td>
            <td>${u.threads}</td>
            <td>${u.lastLogin ? u.lastLogin.replace('T',' ').replace('Z','') : ''}</td>
            <td><button class="button secondary" data-del="${u.id}">Remove</button></td>`
          tbody.appendChild(tr)
        }
      }catch(err){
        if(String(err).includes('unauthorized')){
          // force re-login
          clearToken();
          document.getElementById('login-overlay').hidden = false
          document.getElementById('login-overlay').className = 'overlay'
          return
        }
        stats.textContent = 'Failed to load users'
      }
    }
    el.querySelector('#user-search').addEventListener('click', load)
    tbody.addEventListener('click', async (e) => {
      const btn = e.target.closest('button[data-del]')
      if(!btn) return
      const id = btn.getAttribute('data-del')
      if(!confirm('Remove this user?')) return
      const res = await api(`/users/${id}`, { method:'DELETE' })
      if(res.status === 204){ await load() }
    })
    await load()
    return el
  }
}

async function render(view) {
  state.view = view
  document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === view))
  const content = document.getElementById('content')
  content.innerHTML = ''
  content.appendChild(await views[view]())
}

function boot() {
  loadToken()
  const loginOverlay = document.getElementById('login-overlay')
  const loginForm = document.getElementById('login-form')
  const loginError = document.getElementById('login-error')

  async function ensureAuth(){
    if (!state.token) { loginOverlay.hidden = false; loginOverlay.className='overlay'; return false }
    loginOverlay.hidden = true; loginOverlay.className=''; return true
  }

  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault()
    loginError.textContent = ''
    const fd = new FormData(loginForm)
    const body = JSON.stringify(Object.fromEntries(fd.entries()))
    try{
      const res = await api('/login', { method:'POST', body, headers: {'Content-Type':'application/json'}, })
      const json = await res.json()
      saveToken(json.token)
      await render(state.view)
      await ensureAuth()
    }catch(err){ loginError.textContent = 'Login failed' }
  })

  document.getElementById('nav-menu').addEventListener('click', () => render('menu'))
  document.getElementById('nav-articles').addEventListener('click', () => render('articles'))
  document.getElementById('nav-users').addEventListener('click', () => render('users'))
  document.getElementById('logout').addEventListener('click', () => { clearToken(); render(state.view); loginOverlay.hidden=false; loginOverlay.className='overlay' })
  render(state.view).then(ensureAuth)
}

document.addEventListener('DOMContentLoaded', boot)
