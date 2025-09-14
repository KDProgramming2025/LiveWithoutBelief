import { useEffect, useMemo, useState } from 'react'
import {
  AppBar, Box, Button, Card, CardContent, CircularProgress, Container, Divider, Drawer, FormControl, IconButton, InputLabel, LinearProgress, List, ListItemButton, ListItemIcon, ListItemText, MenuItem, Select, Stack, TextField, Toolbar, Tooltip, Typography, Link as MuiLink, Paper
} from '@mui/material'
import { DataGrid, GridColDef, GridRenderCellParams } from '@mui/x-data-grid'
import ArticleIcon from '@mui/icons-material/Description'
import UsersIcon from '@mui/icons-material/People'
import LogoutIcon from '@mui/icons-material/Logout'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import EditIcon from '@mui/icons-material/Edit'
import SearchIcon from '@mui/icons-material/Search'
import DeleteIcon from '@mui/icons-material/Delete'
import { useColorMode } from './theme'

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
    <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100dvh' }}>
      <Card sx={{ width: 380 }}>
        <CardContent>
          <Stack component="form" onSubmit={submit} spacing={2}>
            <Typography variant="h5" fontWeight={700}>LWB Admin</Typography>
            {err && <Paper variant="outlined" sx={{ p:1.5, borderColor: 'error.main', color: 'error.main' }}>{err}</Paper>}
            <TextField label="Username" value={username} onChange={(e: React.ChangeEvent<HTMLInputElement>)=>setUsername(e.target.value)} autoFocus required fullWidth />
            <TextField label="Password" type="password" value={password} onChange={(e: React.ChangeEvent<HTMLInputElement>)=>setPassword(e.target.value)} required fullWidth />
            <Button type="submit" variant="contained" fullWidth>Login</Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}

export default function App() {
  const api = useJson()
  const [auth, setAuth] = useState<'unknown'|'no'|'yes'>('unknown')
  const [tab, setTab] = useState<'articles'|'users'>('articles')
  const { mode, toggle } = useColorMode()

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
  const [didInitialUserLoad, setDidInitialUserLoad] = useState(false)

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
          if (xhr.status === 409) return reject(new Error('409'))
          return reject(new Error(String(xhr.status)))
        }
        xhr.onerror = () => reject(new Error('network'))
        xhr.send(fd)
      })
    } catch (err: any) {
      const msg = String(err?.message ?? err ?? '')
      if (msg.includes('409')) {
        const ok = confirm('An article with the same ID already exists. Replace it?')
        if (!ok) throw err
        // resend with replace=true
        const fd2 = new FormData()
        if (title) fd2.set('title', title)
        fd2.set('docx', docx)
        if (cover) fd2.set('cover', cover)
        if (icon) fd2.set('icon', icon)
        fd2.set('replace', 'true')
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
          xhr.send(fd2)
        })
      } else {
        throw err
      }
      setTitle(''); setDocx(null); setCover(null); setIcon(null)
      setUploadPct(0)
      const data = await api.get<{ items: Article[] }>(`${API}/v1/admin/articles`); setArticles(data.items)
    } finally { setUploadBusy(false) }
  }

  const searchUsers = async (e: React.FormEvent) => { e.preventDefault(); const res = await api.get<{ query: string; users: UserListItem[] }>(`${API}/v1/admin/users/search?q=${encodeURIComponent(query)}`); setUsers(res.users) }

  // Auto-load latest users when switching to Users tab the first time
  useEffect(() => {
    if (auth !== 'yes') return
    if (tab !== 'users') return
    if (didInitialUserLoad) return
    (async () => {
      try {
        const res = await api.get<{ query: string; users: UserListItem[] }>(`${API}/v1/admin/users/search?q=${encodeURIComponent(query)}`)
        setUsers(res.users)
      } catch {
        // ignore
      } finally {
        setDidInitialUserLoad(true)
      }
    })()
  }, [auth, tab, didInitialUserLoad, api, query])
  const removeUser = async (id: string) => {
    if (!confirm('Are you sure you want to remove this user? This action cannot be undone.')) return;
    try {
      await api.del(`${API}/v1/admin/users/${id}`)
    } catch (err: any) {
      // If already deleted (404), treat as success; rethrow other errors
      const msg = String(err?.message ?? err ?? '')
      if (!msg.includes('404')) throw err
    }
    // refresh total and current search results
    try { const sum = await api.get<UsersSummary>(`${API}/v1/admin/users/summary`); setUsersTotal(sum.total) } catch {}
    try { await searchUsers(new Event('submit') as any) } catch {}
  }

  if (auth === 'unknown') return <Box sx={{ p:3 }}><CircularProgress /></Box>
  if (auth === 'no') return <Login onDone={() => setAuth('yes')} />

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: '280px 1fr', minHeight: '100dvh' }}>
      <Drawer variant="permanent" sx={{ width: 280, [`& .MuiDrawer-paper`]: { width: 280, boxSizing: 'border-box' } }}>
        <Toolbar><Typography variant="h6" fontWeight={700}>LWB Admin</Typography></Toolbar>
        <Divider />
        <List>
          <ListItemButton selected={tab==='articles'} onClick={()=>setTab('articles')}>
            <ListItemIcon><ArticleIcon /></ListItemIcon>
            <ListItemText primary="Articles" secondary="Manage content" />
          </ListItemButton>
          <ListItemButton selected={tab==='users'} onClick={()=>setTab('users')}>
            <ListItemIcon><UsersIcon /></ListItemIcon>
            <ListItemText primary="Users" secondary="Manage accounts" />
          </ListItemButton>
        </List>
        <Box sx={{ flexGrow: 1 }} />
        <Divider />
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ p:2 }}>
          <Tooltip title={mode==='dark'?'Switch to light':'Switch to dark'}>
            <IconButton onClick={toggle} color="inherit">{mode==='dark'?<LightModeIcon/>:<DarkModeIcon/>}</IconButton>
          </Tooltip>
          <Button onClick={logout} color="error" startIcon={<LogoutIcon />}>Logout</Button>
        </Stack>
      </Drawer>
      <Box component="main" sx={{ p: 3 }}>
        <Toolbar />
        {tab === 'articles' && (
          <Stack spacing={3}>
            <Typography variant="h5" fontWeight={700}>Articles</Typography>
            <Card>
              <CardContent>
                <Stack component="form" onSubmit={upload} spacing={2} sx={{ maxWidth: 720 }}>
                  <TextField label="Title (optional)" value={title} onChange={(e: React.ChangeEvent<HTMLInputElement>)=>setTitle(e.target.value)} placeholder="Defaults from docx" fullWidth />
                  <Stack direction={{ xs:'column', sm:'row' }} spacing={2}>
                    <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}>
                      Select DOCX
                      <input hidden type="file" accept=".docx" onChange={e=>setDocx(e.target.files?.[0] ?? null)} />
                    </Button>
                    <Typography sx={{ alignSelf:'center' }} color="text.secondary">{docx?.name ?? 'No file selected'}</Typography>
                  </Stack>
                  <Stack direction={{ xs:'column', sm:'row' }} spacing={2}>
                    <Button variant="outlined" component="label">Cover<input hidden type="file" accept="image/*" onChange={e=>setCover(e.target.files?.[0] ?? null)} /></Button>
                    <Typography sx={{ alignSelf:'center' }} color="text.secondary">{cover?.name ?? 'Optional'}</Typography>
                    <Button variant="outlined" component="label">Icon<input hidden type="file" accept="image/*" onChange={e=>setIcon(e.target.files?.[0] ?? null)} /></Button>
                    <Typography sx={{ alignSelf:'center' }} color="text.secondary">{icon?.name ?? 'Optional'}</Typography>
                  </Stack>
                  {uploadBusy && (
                    <Box>
                      <LinearProgress variant="determinate" value={uploadPct} />
                      <Typography variant="caption" color="text.secondary">{uploadPct}%</Typography>
                    </Box>
                  )}
                  <Stack direction="row" spacing={1}>
                    <Button type="submit" variant="contained" disabled={uploadBusy || !docx}>Upload</Button>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>

            <DataGrid autoHeight disableRowSelectionOnClick rows={articles.slice().sort((a,b)=>a.order-b.order)} getRowId={(r: Article)=>r.id}
              columns={[
                { field: 'order', headerName: 'Order', width: 90 },
                { field: 'title', headerName: 'Title', flex: 1, minWidth: 200 },
                { field: 'filename', headerName: 'Filename', minWidth: 180 },
                { field: 'createdAt', headerName: 'Created', minWidth: 180, valueFormatter: (p:any)=> new Date(p.value as string).toLocaleString() },
                { field: 'updatedAt', headerName: 'Updated', minWidth: 180, valueFormatter: (p:any)=> new Date(p.value as string).toLocaleString() },
                { field: 'cover', headerName: 'Cover', minWidth: 160, renderCell: (p: GridRenderCellParams)=> p.value ? <MuiLink href={p.value as string} target="_blank" rel="noreferrer" underline="hover">Open</MuiLink> : <Typography variant="body2" color="text.secondary">-</Typography> },
                { field: 'icon', headerName: 'Icon', minWidth: 140, renderCell: (p: GridRenderCellParams)=> p.value ? <MuiLink href={p.value as string} target="_blank" rel="noreferrer" underline="hover">Open</MuiLink> : <Typography variant="body2" color="text.secondary">-</Typography> },
                { field: 'publicPath', headerName: 'Public path', minWidth: 220, valueGetter: (p: any) => {
                  const raw = p?.row?.publicPath as string | undefined
                  if (!raw || typeof raw !== 'string') return '-'
                  return raw.replace(/^.*\/LWB\//, '/LWB/')
                } },
                { field: 'actions', headerName: 'Actions', sortable: false, width: 240, renderCell: (p) => (
                  <Stack direction="row" spacing={1}>
                    <Tooltip title="Move up"><span><IconButton size="small" onClick={()=>move(p.row.id,'up')}><ArrowUpwardIcon fontSize="inherit"/></IconButton></span></Tooltip>
                    <Tooltip title="Move down"><span><IconButton size="small" onClick={()=>move(p.row.id,'down')}><ArrowDownwardIcon fontSize="inherit"/></IconButton></span></Tooltip>
                    <Tooltip title="Edit title"><span><IconButton size="small" onClick={()=>{ const t=prompt('New title', p.row.title); if (t!==null) edit(p.row.id, t) }}><EditIcon fontSize="inherit"/></IconButton></span></Tooltip>
                  </Stack>
                ) },
              ] as GridColDef[]}
              pageSizeOptions={[10,25,50]}
              initialState={{ pagination: { paginationModel: { pageSize: 10, page: 0 } } }}
            />
          </Stack>
        )}
        {tab === 'users' && (
          <Stack spacing={2}>
            <Typography variant="h5" fontWeight={700}>Users</Typography>
            <Typography color="text.secondary">Total registered users: {usersTotal}</Typography>
            <Stack component="form" onSubmit={searchUsers} direction={{ xs:'column', sm:'row' }} spacing={1} alignItems={{ sm:'center' }}>
              <TextField value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search username" size="small" />
              <Button type="submit" variant="contained" startIcon={<SearchIcon />}>Search</Button>
              <Typography variant="caption" color="text.secondary">Tip: Leave empty and click Search to list latest users.</Typography>
            </Stack>
            <DataGrid autoHeight disableRowSelectionOnClick rows={users} getRowId={(r)=>r.id}
        columns={[
                { field: 'username', headerName: 'Username', flex: 1, minWidth: 180 },
                { field: 'createdAt', headerName: 'Registered', minWidth: 140, valueFormatter: (p: any) => {
                  const v = p?.value as string | undefined
                  if (!v) return '-'
                  const d = new Date(v)
                  return isNaN(d.getTime()) ? '-' : d.toLocaleDateString()
                } },
                { field: 'bookmarks', headerName: 'Bookmarks', width: 120, valueGetter: (p:any) => {
                  const v = p?.row?.bookmarks
                  return v ?? '-'
                } },
                { field: 'discussions', headerName: 'Threads', width: 120, valueGetter: (p:any) => {
                  const v = p?.row?.discussions
                  return v ?? '-'
                } },
                { field: 'lastLogin', headerName: 'Last login', minWidth: 180, valueGetter: (p:any) => {
                  const v = p?.row?.lastLogin as string | undefined
                  if (!v) return '-'
                  const d = new Date(v)
                  return isNaN(d.getTime()) ? '-' : d.toLocaleString()
                } },
                { field: 'actions', headerName: '', sortable: false, width: 120, renderCell: (p) => (
                  <Button size="small" color="error" startIcon={<DeleteIcon />} onClick={()=>removeUser(p.row.id)}>Remove</Button>
                ) },
              ]}
              pageSizeOptions={[10,25,50]}
              initialState={{ pagination: { paginationModel: { pageSize: 10, page: 0 } } }}
            />
          </Stack>
        )}
      </Box>
    </Box>
  )
}
