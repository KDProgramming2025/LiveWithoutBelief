import { api } from '../core/api.js'
import { clearToken } from '../core/state.js'

export async function viewUsers(){
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
