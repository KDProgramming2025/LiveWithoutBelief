// Minimal router for admin single-page feel without frameworks
const state = { token: null, view: 'menu' }

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
  document.getElementById('nav-menu').addEventListener('click', () => render('menu'))
  document.getElementById('nav-articles').addEventListener('click', () => render('articles'))
  document.getElementById('nav-users').addEventListener('click', () => render('users'))
  document.getElementById('logout').addEventListener('click', () => {/* TODO: logout */})
  render(state.view)
}

document.addEventListener('DOMContentLoaded', boot)
