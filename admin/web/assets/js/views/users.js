import { api } from '../core/api.js'
import { clearToken } from '../core/state.js'
import { fmtLocalDateTime } from '../core/helpers.js'
import { confirm as modalConfirm } from '../ui/modal.js'

export async function viewUsers(){
  const el = document.createElement('div')
  el.innerHTML = `
      <section class="card">
        <h2>Users</h2>
        <div class="row row--search">
          <input class="input" id="user-q" placeholder="Search by username or email" />
          <button class="button secondary" id="user-search">Search</button>
        </div>
        <div id="user-stats" class="badge" style="margin-top:12px"></div>
        <table class="table" id="user-table">
          <thead><tr><th>ID</th><th>User</th><th>Registered</th><th>Bookmarks</th><th>Threads</th><th>Last Login</th><th></th></tr></thead>
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
            <td>${u.id}</td>
            <td>${u.username ?? '(no-username)'}</td>
            <td>${fmtLocalDateTime(u.registeredAt)}</td>
            <td>${u.bookmarks}</td>
            <td>${u.threads}</td>
            <td>${u.lastLogin ? fmtLocalDateTime(u.lastLogin) : ''}</td>
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
    const ok = await modalConfirm('Remove this user?', { danger: true, confirmText: 'Remove' })
    if(!ok) return
    const res = await api(`/users/${id}`, { method:'DELETE' })
    if(res.status === 204){ await load() }
  })
  await load()
  return el
}
