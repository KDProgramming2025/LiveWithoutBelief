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
        <h2>Menu Items</h2>
        <div id="menu-list"></div>
      </section>
      <section class="card">
        <h3>Add Menu Item</h3>
        <form id="menu-form" class="form-grid">
          <input class="input" name="title" placeholder="Title" required />
          <input class="input" name="label" placeholder="Label" />
          <input class="input" name="order" type="number" placeholder="Order" min="0" />
          <input class="input" name="icon" type="file" accept="image/*" />
          <button class="button" type="submit">Add</button>
        </form>
      </section>`
    // TODO: fetch and render items
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
