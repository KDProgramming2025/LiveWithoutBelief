import { useEffect, useMemo, useState } from 'react'

type Article = { id: string; title: string; createdAt: string; updatedAt: string; order: number; filename: string; securePath: string; publicPath: string; cover?: string; icon?: string }
type UsersSummary = { total: number }
type UserListItem = { id: string; username: string; createdAt: string; bookmarks?: number; discussions?: number; lastLogin?: string }

const API = import.meta.env.VITE_API_URL ?? `${location.origin}/LWB/Admin/api`

function useJson() {
  return useMemo(() => ({
  async get<T>(url: string): Promise<T> { const r = await fetch(url, { credentials: 'include', cache: 'no-store' }); if (!r.ok) throw new Error(`${r.status}`); return r.json() },
  async post<T>(url: string, body?: any): Promise<T> { const r = await fetch(url, { method: 'POST', headers: body instanceof FormData ? undefined : { 'Content-Type': 'application/json' }, body: body instanceof FormData ? body : JSON.stringify(body ?? {}), credentials: 'include', cache: 'no-store' }); if (!r.ok) throw new Error(`${r.status}`); return r.json() },
  async del<T>(url: string): Promise<T> { const r = await fetch(url, { method: 'DELETE', credentials: 'include', cache: 'no-store' }); if (!r.ok) throw new Error(`${r.status}`); return r.json() },
  }), [])
}

function Login({ onDone }: { onDone: () => void }) {
  const api = useJson()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null)
    try { await api.post(`${API}/v1/admin/login`, { username, password }); onDone() } catch (e) { setErr('Invalid credentials') }
  }
  return (
    <div style={{ display: 'grid', placeItems: 'center', minHeight: '100dvh', fontFamily: 'Inter, system-ui, Arial' }}>
      <form onSubmit={submit} style={{ width: 340, padding: 24, border: '1px solid #ddd', borderRadius: 12, boxShadow: '0 2px 12px rgba(0,0,0,0.06)', background: '#fff' }}>
        <h1 style={{ marginTop: 0 }}>LWB Admin</h1>
        {err && <div style={{ color: 'crimson', marginBottom: 8 }}>{err}</div>}
        <label>Username<input value={username} onChange={e => setUsername(e.target.value)} required style={{ width: '100%', margin: '6px 0 12px', padding: 8 }} /></label>
        <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} required style={{ width: '100%', margin: '6px 0 16px', padding: 8 }} /></label>
        <button type="submit" style={{ width: '100%', padding: 10 }}>Login</button>
      </form>
    </div>
  )
}

export default function App() {
  const api = useJson()
  const [auth, setAuth] = useState<'unknown'|'no'|'yes'>('unknown')
  const [tab, setTab] = useState<'articles'|'users'>('articles')

  // Article state
  const [articles, setArticles] = useState<Article[]>([])
  const [uploadBusy, setUploadBusy] = useState(false)
  const [uploadPct, setUploadPct] = useState<number>(0)
  const [title, setTitle] = useState('')
  const [docx, setDocx] = useState<File | null>(null)
  const [cover, setCover] = useState<File | null>(null)
  const [icon, setIcon] = useState<File | null>(null)

  // Users state
  const [usersTotal, setUsersTotal] = useState<number>(0)
  const [query, setQuery] = useState('')
  const [users, setUsers] = useState<UserListItem[]>([])

  useEffect(() => { (async () => {
    try { const s = await api.get<{ authenticated: boolean }>(`${API}/v1/admin/session`); setAuth(s.authenticated ? 'yes' : 'no') } catch { setAuth('no') }
  })() }, [])

  useEffect(() => { if (auth !== 'yes') return; (async () => {
    const data = await api.get<{ items: Article[] }>(`${API}/v1/admin/articles`); setArticles(data.items)
    const sum = await api.get<UsersSummary>(`${API}/v1/admin/users/summary`); setUsersTotal(sum.total)
  })() }, [auth])

  const logout = async () => { await api.post(`${API}/v1/admin/logout`); setAuth('no') }

  const move = async (id: string, direction: 'up'|'down') => {
    await api.post(`${API}/v1/admin/articles/${id}/move`, { direction });
    const data = await api.get<{ items: Article[] }>(`${API}/v1/admin/articles`); setArticles(data.items)
  }

  const edit = async (id: string, newTitle?: string) => {
    const fd = new FormData();
    if (newTitle) fd.set('title', newTitle)
    await api.post(`${API}/v1/admin/articles/${id}/edit`, fd)
    const data = await api.get<{ items: Article[] }>(`${API}/v1/admin/articles`); setArticles(data.items)
  }

  const upload = async (e: React.FormEvent) => {
    e.preventDefault(); if (!docx) return; setUploadBusy(true)
    try {
      const fd = new FormData();
      if (title) fd.set('title', title)
      fd.set('docx', docx)
      if (cover) fd.set('cover', cover)
      if (icon) fd.set('icon', icon)
      // Use XHR to get upload progress
      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('POST', `${API}/v1/admin/articles/upload`)
        xhr.withCredentials = true
        xhr.upload.onprogress = (evt) => {
          if (evt.lengthComputable) setUploadPct(Math.round((evt.loaded/evt.total)*100))
        }
        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) return resolve()
          return reject(new Error(String(xhr.status)))
        }
        xhr.onerror = () => reject(new Error('network'))
        xhr.send(fd)
      })
      setTitle(''); setDocx(null); setCover(null); setIcon(null)
      setUploadPct(0)
      const data = await api.get<{ items: Article[] }>(`${API}/v1/admin/articles`); setArticles(data.items)
    } finally { setUploadBusy(false) }
  }

  const searchUsers = async (e: React.FormEvent) => { e.preventDefault(); const res = await api.get<{ query: string; users: UserListItem[] }>(`${API}/v1/admin/users/search?q=${encodeURIComponent(query)}`); setUsers(res.users) }
  const removeUser = async (id: string) => {
    await api.del(`${API}/v1/admin/users/${id}`)
    // refresh total and current search results
    try { const sum = await api.get<UsersSummary>(`${API}/v1/admin/users/summary`); setUsersTotal(sum.total) } catch {}
    await searchUsers(new Event('submit') as any)
  }

  if (auth === 'unknown') return <div style={{ padding: 16 }}>Loading…</div>
  if (auth === 'no') return <Login onDone={() => setAuth('yes')} />

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '240px 1fr', minHeight: '100dvh', fontFamily: 'Inter, system-ui, Arial' }}>
      <aside style={{ borderRight: '1px solid #eee', padding: 16 }}>
        <h2 style={{ marginTop: 0 }}>LWB Admin</h2>
        <nav style={{ display: 'grid', gap: 8 }}>
          <button onClick={() => setTab('articles')} style={{ textAlign: 'left', padding: 8, background: tab==='articles'?'#f0f0f0':'transparent' }}>Article management</button>
          <button onClick={() => setTab('users')} style={{ textAlign: 'left', padding: 8, background: tab==='users'?'#f0f0f0':'transparent' }}>User Management</button>
          <button onClick={logout} style={{ textAlign: 'left', padding: 8, color: 'crimson' }}>Logout</button>
        </nav>
      </aside>
      <main style={{ padding: 24 }}>
        {tab === 'articles' && (
          <div>
            <h2>Articles</h2>
            <form onSubmit={upload} style={{ display: 'grid', gap: 8, maxWidth: 640, padding: 12, border: '1px solid #ddd', borderRadius: 8 }}>
              <div>
                <label>Title<br/><input value={title} onChange={e=>setTitle(e.target.value)} placeholder="Optional; defaults from docx" style={{ width: '100%', padding: 8 }} /></label>
              </div>
              <div>
                <label>DOCX<br/><input type="file" accept=".docx" onChange={e => setDocx(e.target.files?.[0] ?? null)} required /></label>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <label>Cover<br/><input type="file" accept="image/*" onChange={e => setCover(e.target.files?.[0] ?? null)} /></label>
                <label>Icon<br/><input type="file" accept="image/*" onChange={e => setIcon(e.target.files?.[0] ?? null)} /></label>
              </div>
              {uploadBusy && (
                <div style={{ display:'grid', gap:6 }}>
                  <div style={{ height:8, background:'#eee', borderRadius:4, overflow:'hidden' }}>
                    <div style={{ width:`${uploadPct}%`, height:'100%', background:'#3b82f6', transition:'width .2s' }} />
                  </div>
                  <small>{uploadPct}%</small>
                </div>
              )}
              <div><button type="submit" disabled={uploadBusy}>Upload</button></div>
            </form>

            <table cellPadding={6} style={{ marginTop: 16, borderCollapse: 'collapse' }}>
              <thead><tr><th>Order</th><th>Title</th><th>Filename</th><th>Created</th><th>Updated</th><th>Cover</th><th>Icon</th><th>Path</th><th>Actions</th></tr></thead>
              <tbody>
                {articles.slice().sort((a,b)=>a.order-b.order).map(a => (
                  <tr key={a.id}>
                    <td>{a.order}</td>
                    <td>{a.title}</td>
                    <td>{a.filename}</td>
                    <td>{new Date(a.createdAt).toLocaleString()}</td>
                    <td>{new Date(a.updatedAt).toLocaleString()}</td>
                    <td>{a.cover || '-'}</td>
                    <td>{a.icon || '-'}</td>
                    <td>{a.publicPath.replace(/^.*\/LWB\//, '/LWB/')}</td>
                    <td>
                      <button onClick={()=>move(a.id,'up')}>Up</button>{' '}
                      <button onClick={()=>move(a.id,'down')}>Down</button>{' '}
                      <button onClick={()=>{ const t=prompt('New title', a.title); if (t!==null) edit(a.id, t) }}>Edit title</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {tab === 'users' && (
          <div>
            <h2>Users</h2>
            <p>Total registered users: {usersTotal}</p>
            <form onSubmit={searchUsers} style={{ display: 'flex', gap: 8 }}>
              <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search username" style={{ padding: 8 }} />
              <button type="submit">Search</button>
            </form>
            <table cellPadding={6} style={{ marginTop: 12 }}>
              <thead><tr><th>Username</th><th>Registered</th><th>Bookmarks</th><th>Threads</th><th>Last login</th><th></th></tr></thead>
              <tbody>
                {users.map(u => (
                  <tr key={u.id}>
                    <td>{u.username}</td>
                    <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                    <td>{u.bookmarks ?? '-'}</td>
                    <td>{u.discussions ?? '-'}</td>
                    <td>{u.lastLogin ? new Date(u.lastLogin).toLocaleString() : '-'}</td>
                    <td><button onClick={()=>removeUser(u.id)} style={{ color: 'crimson' }}>Remove</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  )
}
