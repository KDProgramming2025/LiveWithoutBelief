import { useEffect, useMemo, useState } from 'react'
import {
  AppBar, Avatar, Box, Button, Card, CardActions, CardContent, CircularProgress, Divider, Drawer, IconButton, LinearProgress, List, ListItemButton, ListItemIcon, ListItemText, Stack, TextField, Toolbar, Tooltip, Typography, Link as MuiLink, Paper, Grid, Chip, CardMedia, Snackbar, Alert
} from '@mui/material'
import { DataGrid } from '@mui/x-data-grid'
import ArticleIcon from '@mui/icons-material/Description'
import UsersIcon from '@mui/icons-material/People'
import MenuIcon from '@mui/icons-material/Menu'
import LogoutIcon from '@mui/icons-material/Logout'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import EditIcon from '@mui/icons-material/Edit'
import ImageIcon from '@mui/icons-material/Image'
import InsertPhotoIcon from '@mui/icons-material/InsertPhoto'
import SearchIcon from '@mui/icons-material/Search'
import DeleteIcon from '@mui/icons-material/Delete'
import { useColorMode } from './theme'
import { ConfirmDialog, SingleFieldDialog, TwoFieldDialog } from './components/Dialogs'

type Article = { id: string; title: string; createdAt: string; updatedAt: string; order: number; filename: string; securePath: string; publicPath: string; cover?: string; icon?: string }
type MenuItem = { id: string; title: string; label: string; order: number; updatedAt: string; icon?: string }
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
  const [tab, setTab] = useState<'menu'|'articles'|'users'>('menu')
  const { mode, toggle } = useColorMode()

  // Article state
  const [menu, setMenu] = useState<MenuItem[]>([])
  const [menuTitle, setMenuTitle] = useState('')
  const [menuLabel, setMenuLabel] = useState('')
  const [menuOrder, setMenuOrder] = useState<number|''>('')
  const [menuIcon, setMenuIcon] = useState<File | null>(null)
  const [menuBusy, setMenuBusy] = useState(false)
  const [articles, setArticles] = useState<Article[]>([])
  const [uploadBusy, setUploadBusy] = useState(false)
  const [uploadPct, setUploadPct] = useState<number>(0)
  const [title, setTitle] = useState('')
  const [docx, setDocx] = useState<File | null>(null)
  const [cover, setCover] = useState<File | null>(null)
  const [icon, setIcon] = useState<File | null>(null)
  // Per-article busy state for quick edits (cover/icon/title)
  const [busyIds, setBusyIds] = useState<Record<string, boolean>>({})
  const setBusy = (id: string, v: boolean) => setBusyIds(prev => ({ ...prev, [id]: v }))
  // Per-menu-item busy state (icon upload/edit)
  const [menuBusyIds, setMenuBusyIds] = useState<Record<string, boolean>>({})
  const setMenuBusyId = (id: string, v: boolean) => setMenuBusyIds(prev => ({ ...prev, [id]: v }))
  // Local preview for menu icon while uploading
  const [menuLocalPreview, setMenuLocalPreview] = useState<Record<string, string | undefined>>({})
  // Image retry state to mitigate transient HTTP/2 load errors
  const [coverRetry, setCoverRetry] = useState<Record<string, number>>({})
  const [iconRetry, setIconRetry] = useState<Record<string, number>>({})
  const [coverErrCount, setCoverErrCount] = useState<Record<string, number>>({})
  const [iconErrCount, setIconErrCount] = useState<Record<string, number>>({})
  // Toast feedback
  const [toast, setToast] = useState<{ open: boolean; message: string; severity: 'success'|'error' }>({ open: false, message: '', severity: 'success' })
  const showToast = (message: string, severity: 'success'|'error' = 'success') => setToast({ open: true, message, severity })

  // Users state
  const [usersTotal, setUsersTotal] = useState<number>(0)
  const [query, setQuery] = useState('')
  const [users, setUsers] = useState<UserListItem[]>([])
  const [didInitialUserLoad, setDidInitialUserLoad] = useState(false)

  useEffect(() => { (async () => {
    try { const s = await api.get<{ authenticated: boolean }>(`${API}/v1/admin/session`); setAuth(s.authenticated ? 'yes' : 'no') } catch { setAuth('no') }
  })() }, [])

  useEffect(() => { if (auth !== 'yes') return; (async () => {
    try { const m = await api.get<{ items: MenuItem[] }>(`${API}/v1/admin/menu`); setMenu(m.items) } catch {}
    try { const data = await api.get<{ items: Article[] }>(`${API}/v1/admin/articles`); setArticles(data.items) } catch {}
    try { const sum = await api.get<UsersSummary>(`${API}/v1/admin/users/summary`); setUsersTotal(sum.total) } catch {}
  })() }, [auth])
  const refreshMenu = async () => { const m = await api.get<{ items: MenuItem[] }>(`${API}/v1/admin/menu`); setMenu(m.items) }
  const editMenu = async (id: string, updates: { title?: string; label?: string; icon?: File }) => {
    const fd = new FormData()
    if (typeof updates.title === 'string') fd.set('title', updates.title)
    if (typeof updates.label === 'string') fd.set('label', updates.label)
    if (updates.icon) fd.set('icon', updates.icon)
    await api.post(`${API}/v1/admin/menu/${id}/edit`, fd)
    await refreshMenu()
  }
  const changeMenuIcon = async (id: string) => {
    const file = await pickImage(); if (!file) return
    // Show immediate local preview and busy overlay while uploading
    const url = URL.createObjectURL(file)
    setMenuLocalPreview(prev => ({ ...prev, [id]: url }))
    setMenuBusyId(id, true)
    try {
      await editMenu(id, { icon: file })
      showToast('Menu icon updated')
    } catch {
      showToast('Failed to update icon','error')
    } finally {
      setMenuBusyId(id, false)
      // Revoke and clear local preview
      try { URL.revokeObjectURL(url) } catch {}
      setMenuLocalPreview(prev => { const { [id]: _, ...rest } = prev; return rest })
    }
  }
  const addMenuItem = async (e: React.FormEvent) => {
    e.preventDefault(); setMenuBusy(true);
    try {
      const fd = new FormData()
      fd.set('title', menuTitle)
      fd.set('label', menuLabel)
      if (menuOrder !== '') fd.set('order', String(menuOrder))
      if (menuIcon) fd.set('icon', menuIcon)
      await api.post(`${API}/v1/admin/menu`, fd)
      setMenuTitle(''); setMenuLabel(''); setMenuOrder(''); setMenuIcon(null)
      await refreshMenu()
      showToast('Menu item added','success')
    } catch {
      showToast('Failed to add menu item','error')
    } finally { setMenuBusy(false) }
  }
  const moveMenu = async (id: string, direction:'up'|'down') => { await api.post(`${API}/v1/admin/menu/${id}/move`, { direction }); await refreshMenu() }
  // Delete dialogs state
  const [menuToDelete, setMenuToDelete] = useState<MenuItem | null>(null)
  const confirmDeleteMenu = async () => { if (!menuToDelete) return; await api.del(`${API}/v1/admin/menu/${menuToDelete.id}`); setMenuToDelete(null); await refreshMenu(); showToast('Menu item deleted') }

  const logout = async () => { await api.post(`${API}/v1/admin/logout`); setAuth('no') }

  const move = async (id: string, direction: 'up'|'down') => {
    await api.post(`${API}/v1/admin/articles/${id}/move`, { direction });
    const data = await api.get<{ items: Article[] }>(`${API}/v1/admin/articles`); setArticles(data.items)
  }

  // Generic updater used by title/cover/icon updates
  const updateArticle = async (id: string, opts?: { title?: string; cover?: File; icon?: File }) => {
    const fd = new FormData()
    if (opts?.title) fd.set('title', opts.title)
    if (opts?.cover) fd.set('cover', opts.cover)
    if (opts?.icon) fd.set('icon', opts.icon)
    const res = await api.post<{ ok: boolean; item: Article; warnings?: Array<{ field: 'cover'|'icon'; error: string }> }>(`${API}/v1/admin/articles/${id}/edit`, fd)
    // Surface server-side warnings if any
    if (Array.isArray(res?.warnings) && res.warnings.length) {
      const msg = res.warnings.map(w => `${w.field}: ${w.error.replace(/_/g,' ')}`).join(', ')
      showToast(`Upload warnings: ${msg}`, 'error')
    }
    const data = await api.get<{ items: Article[] }>(`${API}/v1/admin/articles`); setArticles(data.items)
  }

  const edit = async (id: string, newTitle?: string) => updateArticle(id, { title: newTitle })
  const [articleToEdit, setArticleToEdit] = useState<Article | null>(null)
  const [menuToEdit, setMenuToEdit] = useState<MenuItem | null>(null)

  // Utility to prompt for a single image file using a transient input element
  const pickImage = (): Promise<File | null> => new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.onchange = () => resolve(input.files?.[0] ?? null)
    input.click()
  })

  const changeCover = async (id: string) => {
    const file = await pickImage(); if (!file) return
    setBusy(id, true)
    try {
      await updateArticle(id, { cover: file })
      showToast('Cover updated', 'success')
    } catch (e) {
      showToast('Failed to update cover', 'error')
    } finally {
      setBusy(id, false)
    }
  }

  const changeIcon = async (id: string) => {
    const file = await pickImage(); if (!file) return
    setBusy(id, true)
    try {
      await updateArticle(id, { icon: file })
      showToast('Icon updated', 'success')
    } catch (e) {
      showToast('Failed to update icon', 'error')
    } finally {
      setBusy(id, false)
    }
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
        // Ask via dialog
        setReplaceDialogOpen(true)
        const ok: boolean = await new Promise(resolve => setReplaceDialogResolve(()=>resolve))
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
  const [userToRemove, setUserToRemove] = useState<UserListItem | null>(null)

  // Normalize any backend-returned path to a public URL the browser can load
  // - Converts backslashes to forward slashes
  // - Trims any server FS prefix before /LWB/
  // - Leaves absolute http(s) URLs intact
  const normalizePublicUrl = (u?: string): string | '' => {
    if (!u) return ''
    const s = u.replace(/\\/g, '/').trim()
    if (/^https?:\/\//i.test(s)) return s
    const idx = s.indexOf('/LWB/')
    if (idx >= 0) return s.slice(idx)
    return s.startsWith('/') ? s : `/${s}`
  }
  // Force canonical Menu icon path to /LWB/Admin/Menu to avoid legacy upstream decoy
  const toCanonicalMenuUrl = (u?: string): string | '' => {
    const s = normalizePublicUrl(u)
    if (!s) return ''
    return s.replace(/\/LWB\/Menu\//, '/LWB/Admin/Menu/')
  }

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
    const u = users.find(x=>x.id===id) || null
    setUserToRemove(u)
  }
  const confirmRemoveUser = async () => {
    if (!userToRemove) return
    try {
      await api.del(`${API}/v1/admin/users/${userToRemove.id}`)
    } catch (err: any) {
      // If already deleted (404), treat as success; rethrow other errors
      const msg = String(err?.message ?? err ?? '')
      if (!msg.includes('404')) throw err
    }
    // refresh total and current search results
    try { const sum = await api.get<UsersSummary>(`${API}/v1/admin/users/summary`); setUsersTotal(sum.total) } catch {}
    try { await searchUsers(new Event('submit') as any) } catch {}
    setUserToRemove(null)
    showToast('User removed')
  }

  // Replace-upload dialog state
  const [replaceDialogOpen, setReplaceDialogOpen] = useState(false)
  const [replaceDialogResolve, setReplaceDialogResolve] = useState<(ok: boolean) => void>(()=>()=>{})

  if (auth === 'unknown') return <Box sx={{ p:3 }}><CircularProgress /></Box>
  if (auth === 'no') return <Login onDone={() => setAuth('yes')} />

  return (
  <Box sx={{ display: 'grid', gridTemplateColumns: '280px 1fr', minHeight: '100dvh', overflowX: 'hidden' }}>
      <Drawer variant="permanent" sx={{ width: 280, [`& .MuiDrawer-paper`]: { width: 280, boxSizing: 'border-box' } }}>
        <Toolbar><Typography variant="h6" fontWeight={700}>LWB Admin</Typography></Toolbar>
        <Divider />
        <List>
          <ListItemButton selected={tab==='menu'} onClick={()=>setTab('menu')}>
            <ListItemIcon><MenuIcon /></ListItemIcon>
            <ListItemText primary="App Main Menu" secondary="Manage client menu" />
          </ListItemButton>
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
  <Box component="main" sx={{ p: 3, overflowX: 'hidden' }}>
        <Toolbar />
        {tab === 'menu' && (
          <Stack spacing={3}>
            <Typography variant="h5" fontWeight={700}>App Main Menu</Typography>
            <Card>
              <CardContent>
                <Stack component="form" onSubmit={addMenuItem} spacing={2} sx={{ maxWidth: 720 }}>
                  <TextField label="Title" value={menuTitle} onChange={(e: React.ChangeEvent<HTMLInputElement>)=>setMenuTitle(e.target.value)} required fullWidth />
                  <Stack direction={{ xs:'column', sm:'row' }} spacing={2}>
                    <TextField label="Label tag" value={menuLabel} onChange={(e: React.ChangeEvent<HTMLInputElement>)=>setMenuLabel(e.target.value)} required fullWidth />
                    <TextField label="Order (optional)" type="number" value={menuOrder} onChange={(e)=>setMenuOrder(e.target.value === '' ? '' : Number(e.target.value))} sx={{ width: 200 }} />
                  </Stack>
                  <Stack direction={{ xs:'column', sm:'row' }} spacing={2}>
                    <Button variant="outlined" component="label">Icon image<input hidden type="file" accept="image/*" onChange={e=>setMenuIcon(e.target.files?.[0] ?? null)} /></Button>
                    <Typography sx={{ alignSelf:'center' }} color="text.secondary">{menuIcon?.name ?? 'Optional'}</Typography>
                  </Stack>
                  <Button type="submit" variant="contained" disabled={menuBusy}>Add Menu Item</Button>
                </Stack>
              </CardContent>
            </Card>

            <Grid container spacing={2}>
              {menu.slice().sort((a,b)=>a.order-b.order).map(m => {
                const bust = m.updatedAt ? encodeURIComponent(m.updatedAt) : String(m.order)
                const baseIcon = toCanonicalMenuUrl(m.icon)
                const liveOrPreview = menuLocalPreview[m.id] || baseIcon
                const attempts = iconErrCount[m.id] ?? 0
                const iconSrc = liveOrPreview && attempts <= 2 ? `${liveOrPreview}${liveOrPreview.includes('?') ? '&' : '?'}v=${bust}${attempts>0?`&r=${Date.now()}`:''}` : undefined
                return (
                <Grid item xs={12} sm={6} md={4} lg={3} key={m.id}>
                  <Card>
                    <Box sx={{ position:'relative' }}>
                    <CardContent>
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={1} alignItems="center">
                          {iconSrc ? (
                            <Box component="img" src={iconSrc} alt="Icon" sx={{ width: 28, height: 28, borderRadius: 1 }}
                              onError={() => {
                                setIconErrCount(prev => ({ ...prev, [m.id]: (prev[m.id] ?? 0) + 1 }))
                              }}
                            />
                          ) : (
                            <Box sx={{ width: 28, height: 28, borderRadius: 1, bgcolor: 'action.hover', display:'grid', placeItems:'center' }}>
                              <InsertPhotoIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                            </Box>
                          )}
                          <Typography variant="subtitle1" fontWeight={700} noWrap title={m.title}>{m.title}</Typography>
                        </Stack>
                        <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                          <Chip size="small" label={`Label: ${m.label}`} />
                          <Tooltip title="Edit label/title"><span><IconButton size="small" onClick={()=> setMenuToEdit(m)} disabled={!!menuBusyIds[m.id]}><EditIcon fontSize="inherit"/></IconButton></span></Tooltip>
                          <Tooltip title="Change icon"><span><IconButton size="small" onClick={()=>changeMenuIcon(m.id)} disabled={!!menuBusyIds[m.id]}><InsertPhotoIcon fontSize="inherit"/></IconButton></span></Tooltip>
                        </Stack>
                        <Chip size="small" variant="outlined" label={`Order #${m.order}`} />
                      </Stack>
                    </CardContent>
                    {menuBusyIds[m.id] && (
                      <Box sx={{ position:'absolute', inset:0, display:'grid', placeItems:'center', bgcolor:'rgba(0,0,0,0.25)' }}>
                        <CircularProgress size={28} />
                      </Box>
                    )}
                    </Box>
                    <CardActions>
                      <Stack direction="row" spacing={1}>
                        <Tooltip title="Move up"><span><IconButton size="small" onClick={()=>moveMenu(m.id,'up')} disabled={!!menuBusyIds[m.id]}><ArrowUpwardIcon fontSize="inherit"/></IconButton></span></Tooltip>
                        <Tooltip title="Move down"><span><IconButton size="small" onClick={()=>moveMenu(m.id,'down')} disabled={!!menuBusyIds[m.id]}><ArrowDownwardIcon fontSize="inherit"/></IconButton></span></Tooltip>
                        <Tooltip title="Delete"><span><IconButton size="small" onClick={()=>setMenuToDelete(m)} color="error" disabled={!!menuBusyIds[m.id]}><DeleteIcon fontSize="inherit"/></IconButton></span></Tooltip>
                      </Stack>
                    </CardActions>
                  </Card>
                </Grid>
              )})}
            </Grid>
          </Stack>
        )}
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

            <Grid container spacing={2}>
              {articles.slice().sort((a,b)=>a.order-b.order).map(a => {
                const created = a.createdAt ? new Date(a.createdAt) : null
                const updated = a.updatedAt ? new Date(a.updatedAt) : null
                const publicLink = typeof a.publicPath === 'string' ? a.publicPath.replace(/^.*\/LWB\//, '/LWB/') : null
                const bust = a.updatedAt ? (a.updatedAt.includes('?') ? a.updatedAt : encodeURIComponent(a.updatedAt)) : String(a.order)
                const coverBase = a.cover ? `${a.cover}${a.cover.includes('?') ? '&' : '?'}v=${bust}` : null
                const iconBase = a.icon ? `${a.icon}${a.icon.includes('?') ? '&' : '?'}v=${bust}` : undefined
                const coverAttempts = coverErrCount[a.id] ?? 0
                const iconAttempts = iconErrCount[a.id] ?? 0
                const coverSrc = coverBase && coverAttempts <= 2 ? `${coverBase}${coverRetry[a.id] ? `&r=${coverRetry[a.id]}` : ''}` : null
                const iconSrc = iconBase && iconAttempts <= 2 ? `${iconBase}${iconRetry[a.id] ? `&r=${iconRetry[a.id]}` : ''}` : undefined
                return (
                  <Grid item xs={12} sm={6} md={4} lg={3} key={a.id}>
                    <Card sx={{ height:'100%', display:'flex', flexDirection:'column' }}>
                      <Box sx={{ position:'relative' }}>
                        {coverSrc ? (
                          <CardMedia component="img" src={coverSrc} alt="Cover" sx={{ aspectRatio:'16/9', objectFit:'cover' }} onError={() => {
                            setCoverErrCount(prev => {
                              const attempts = (prev[a.id] ?? 0) + 1
                              if (attempts <= 2) {
                                setCoverRetry(r => ({ ...r, [a.id]: Date.now() }))
                              }
                              return { ...prev, [a.id]: attempts }
                            })
                          }} />
                        ) : (
                          <Box sx={{ aspectRatio:'16/9', display:'grid', placeItems:'center', bgcolor:'action.hover' }}>
                            <ArticleIcon sx={{ fontSize: 48, color:'text.secondary' }} />
                          </Box>
                        )}
                        {busyIds[a.id] && (
                          <Box sx={{ position:'absolute', inset:0, display:'grid', placeItems:'center', bgcolor:'rgba(0,0,0,0.25)' }}>
                            <CircularProgress size={28} />
                          </Box>
                        )}
                      </Box>
                      <CardContent sx={{ flexGrow: 1 }}>
                        <Stack spacing={1}>
                          <Stack direction="row" spacing={1} alignItems="center" minWidth={0}>
                            {iconSrc ? (
                              <Avatar src={iconSrc} variant="rounded" sx={{ width: 28, height: 28 }}
                                imgProps={{ onError: () => {
                                  setIconErrCount(prev => {
                                    const attempts = (prev[a.id] ?? 0) + 1
                                    if (attempts <= 2) {
                                      setIconRetry(r => ({ ...r, [a.id]: Date.now() }))
                                    }
                                    return { ...prev, [a.id]: attempts }
                                  })
                                } }}
                              />
                            ) : (
                              <Avatar variant="rounded" sx={{ width: 28, height: 28, bgcolor: 'action.hover' }}>
                                <InsertPhotoIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                              </Avatar>
                            )}
                            <Typography variant="subtitle1" fontWeight={700} noWrap title={a.title || a.filename}>
                              {a.title || a.filename}
                            </Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary" noWrap title={a.filename}>{a.filename}</Typography>
                          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                            <Chip size="small" label={`Order #${a.order}`} />
                            {created && <Chip size="small" variant="outlined" label={`Created ${created.toLocaleDateString()}`} />}
                            {updated && <Chip size="small" variant="outlined" label={`Updated ${updated.toLocaleDateString()}`} />}
                          </Stack>
                          {publicLink && <MuiLink href={publicLink} target="_blank" rel="noreferrer" underline="hover">Open public link</MuiLink>}
                        </Stack>
                      </CardContent>
                      <CardActions sx={{ justifyContent:'space-between' }}>
                        <Stack direction="row" spacing={1}>
                          <Tooltip title="Move up"><span><IconButton size="small" onClick={()=>move(a.id,'up')}><ArrowUpwardIcon fontSize="inherit"/></IconButton></span></Tooltip>
                          <Tooltip title="Move down"><span><IconButton size="small" onClick={()=>move(a.id,'down')}><ArrowDownwardIcon fontSize="inherit"/></IconButton></span></Tooltip>
                          <Tooltip title="Edit title"><span><IconButton size="small" onClick={()=> setArticleToEdit(a)}><EditIcon fontSize="inherit"/></IconButton></span></Tooltip>
                          <Tooltip title="Change cover"><span><IconButton size="small" onClick={()=>changeCover(a.id)} disabled={!!busyIds[a.id]}><ImageIcon fontSize="inherit"/></IconButton></span></Tooltip>
                          <Tooltip title="Change icon"><span><IconButton size="small" onClick={()=>changeIcon(a.id)} disabled={!!busyIds[a.id]}><InsertPhotoIcon fontSize="inherit"/></IconButton></span></Tooltip>
                        </Stack>
                      </CardActions>
                    </Card>
                  </Grid>
                )
              })}
            </Grid>
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
                { field: 'createdAt', headerName: 'Registered', minWidth: 160, valueGetter: (p: any) => p?.row?.createdAt ?? null, renderCell: (p: any) => {
                  const v = p?.row?.createdAt as string | undefined
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
                { field: 'lastLogin', headerName: 'Last login', minWidth: 180, valueGetter: (p:any) => p?.row?.lastLogin ?? null, renderCell: (p:any) => {
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
        <Snackbar open={toast.open} autoHideDuration={2500} onClose={() => setToast(t => ({ ...t, open: false }))} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
          <Alert onClose={() => setToast(t => ({ ...t, open: false }))} severity={toast.severity} sx={{ width: '100%' }}>{toast.message}</Alert>
        </Snackbar>

        {/* Dialogs */}
        <TwoFieldDialog
          open={!!menuToEdit}
          title="Edit menu item"
          aLabel="Title"
          bLabel="Label"
          initialA={menuToEdit?.title ?? ''}
          initialB={menuToEdit?.label ?? ''}
          onSubmit={async (title, label) => { if (!menuToEdit) return; await editMenu(menuToEdit.id, { title, label }); showToast('Menu item updated'); setMenuToEdit(null) }}
          onClose={() => setMenuToEdit(null)}
        />

        <SingleFieldDialog
          open={!!articleToEdit}
          title="Edit article title"
          label="Title"
          initial={articleToEdit?.title ?? ''}
          onSubmit={async (title) => { if (!articleToEdit) return; await edit(articleToEdit.id, title); showToast('Article updated') }}
          onClose={() => setArticleToEdit(null)}
        />

        <ConfirmDialog
          open={!!menuToDelete}
          title="Delete menu item"
          message={menuToDelete ? `Are you sure you want to delete "${menuToDelete.title}"?` : ''}
          confirmText="Delete"
          onClose={(ok) => { if (ok) confirmDeleteMenu(); else setMenuToDelete(null) }}
        />

        <ConfirmDialog
          open={!!userToRemove}
          title="Remove user"
          message={userToRemove ? `Remove user "${userToRemove.username}"? This cannot be undone.` : ''}
          confirmText="Remove"
          onClose={(ok) => { if (ok) confirmRemoveUser(); else setUserToRemove(null) }}
        />

        <ConfirmDialog
          open={replaceDialogOpen}
          title="Replace existing article?"
          message="An article with the same ID already exists. Do you want to replace it?"
          confirmText="Replace"
          onClose={(ok) => { const r = replaceDialogResolve; setReplaceDialogOpen(false); setReplaceDialogResolve(()=>()=>{}); r(ok) }}
        />
      </Box>
    </Box>
  )
}
