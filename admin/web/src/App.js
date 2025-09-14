import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useMemo, useState } from 'react';
import { Avatar, Box, Button, Card, CardActions, CardContent, CircularProgress, Divider, Drawer, IconButton, LinearProgress, List, ListItemButton, ListItemIcon, ListItemText, Stack, TextField, Toolbar, Tooltip, Typography, Link as MuiLink, Paper, Grid, Chip, CardMedia, Snackbar, Alert } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import ArticleIcon from '@mui/icons-material/Description';
import UsersIcon from '@mui/icons-material/People';
import LogoutIcon from '@mui/icons-material/Logout';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import EditIcon from '@mui/icons-material/Edit';
import ImageIcon from '@mui/icons-material/Image';
import InsertPhotoIcon from '@mui/icons-material/InsertPhoto';
import SearchIcon from '@mui/icons-material/Search';
import DeleteIcon from '@mui/icons-material/Delete';
import { useColorMode } from './theme';
const API = import.meta.env.VITE_API_URL ?? `${location.origin}/LWB/Admin/api`;
function useJson() {
    return useMemo(() => ({
        async get(url) { const r = await fetch(url, { credentials: 'include', cache: 'no-store' }); if (!r.ok)
            throw new Error(`${r.status}`); return r.json(); },
        async post(url, body) { const r = await fetch(url, { method: 'POST', headers: body instanceof FormData ? undefined : { 'Content-Type': 'application/json' }, body: body instanceof FormData ? body : JSON.stringify(body ?? {}), credentials: 'include', cache: 'no-store' }); if (!r.ok)
            throw new Error(`${r.status}`); return r.json(); },
        async del(url) { const r = await fetch(url, { method: 'DELETE', credentials: 'include', cache: 'no-store' }); if (!r.ok)
            throw new Error(`${r.status}`); return r.json(); },
    }), []);
}
function Login({ onDone }) {
    const api = useJson();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [err, setErr] = useState(null);
    const submit = async (e) => {
        e.preventDefault();
        setErr(null);
        try {
            await api.post(`${API}/v1/admin/login`, { username, password });
            onDone();
        }
        catch (e) {
            setErr('Invalid credentials');
        }
    };
    return (_jsx(Box, { sx: { display: 'grid', placeItems: 'center', minHeight: '100dvh' }, children: _jsx(Card, { sx: { width: 380 }, children: _jsx(CardContent, { children: _jsxs(Stack, { component: "form", onSubmit: submit, spacing: 2, children: [_jsx(Typography, { variant: "h5", fontWeight: 700, children: "LWB Admin" }), err && _jsx(Paper, { variant: "outlined", sx: { p: 1.5, borderColor: 'error.main', color: 'error.main' }, children: err }), _jsx(TextField, { label: "Username", value: username, onChange: (e) => setUsername(e.target.value), autoFocus: true, required: true, fullWidth: true }), _jsx(TextField, { label: "Password", type: "password", value: password, onChange: (e) => setPassword(e.target.value), required: true, fullWidth: true }), _jsx(Button, { type: "submit", variant: "contained", fullWidth: true, children: "Login" })] }) }) }) }));
}
export default function App() {
    const api = useJson();
    const [auth, setAuth] = useState('unknown');
    const [tab, setTab] = useState('articles');
    const { mode, toggle } = useColorMode();
    // Article state
    const [articles, setArticles] = useState([]);
    const [uploadBusy, setUploadBusy] = useState(false);
    const [uploadPct, setUploadPct] = useState(0);
    const [title, setTitle] = useState('');
    const [docx, setDocx] = useState(null);
    const [cover, setCover] = useState(null);
    const [icon, setIcon] = useState(null);
    // Users state
    const [usersTotal, setUsersTotal] = useState(0);
    const [query, setQuery] = useState('');
    const [users, setUsers] = useState([]);
    const [didInitialUserLoad, setDidInitialUserLoad] = useState(false);
    // Quick edit busy and toast
    const [busyIds, setBusyIds] = useState({});
    const setBusy = (id, v) => setBusyIds(prev => ({ ...prev, [id]: v }));
    const [toast, setToast] = useState({ open: false, message: '', severity: 'success' });
    const showToast = (message, severity = 'success') => setToast({ open: true, message, severity });
    useEffect(() => {
        (async () => {
            try {
                const s = await api.get(`${API}/v1/admin/session`);
                setAuth(s.authenticated ? 'yes' : 'no');
            }
            catch {
                setAuth('no');
            }
        })();
    }, []);
    useEffect(() => {
        if (auth !== 'yes')
            return;
        (async () => {
            const data = await api.get(`${API}/v1/admin/articles`);
            setArticles(data.items);
            const sum = await api.get(`${API}/v1/admin/users/summary`);
            setUsersTotal(sum.total);
        })();
    }, [auth]);
    const logout = async () => { await api.post(`${API}/v1/admin/logout`); setAuth('no'); };
    const move = async (id, direction) => {
        await api.post(`${API}/v1/admin/articles/${id}/move`, { direction });
        const data = await api.get(`${API}/v1/admin/articles`);
        setArticles(data.items);
    };
    const updateArticle = async (id, opts) => {
        const fd = new FormData();
        if (opts?.title)
            fd.set('title', opts.title);
        if (opts?.cover)
            fd.set('cover', opts.cover);
        if (opts?.icon)
            fd.set('icon', opts.icon);
        await api.post(`${API}/v1/admin/articles/${id}/edit`, fd);
        const data = await api.get(`${API}/v1/admin/articles`);
        setArticles(data.items);
    };
    const edit = async (id, newTitle) => updateArticle(id, { title: newTitle });
    const pickImage = () => new Promise((resolve) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = () => resolve(input.files?.[0] ?? null);
        input.click();
    });
    const changeCover = async (id) => {
        const file = await pickImage();
        if (!file)
            return;
        setBusy(id, true);
        try {
            await updateArticle(id, { cover: file });
            showToast('Cover updated', 'success');
        }
        catch (_a) {
            showToast('Failed to update cover', 'error');
        }
        finally {
            setBusy(id, false);
        }
    };
    const changeIcon = async (id) => {
        const file = await pickImage();
        if (!file)
            return;
        setBusy(id, true);
        try {
            await updateArticle(id, { icon: file });
            showToast('Icon updated', 'success');
        }
        catch (_a) {
            showToast('Failed to update icon', 'error');
        }
        finally {
            setBusy(id, false);
        }
    };
    const upload = async (e) => {
        e.preventDefault();
        if (!docx)
            return;
        setUploadBusy(true);
        try {
            const fd = new FormData();
            if (title)
                fd.set('title', title);
            fd.set('docx', docx);
            if (cover)
                fd.set('cover', cover);
            if (icon)
                fd.set('icon', icon);
            // Use XHR to get upload progress
            await new Promise((resolve, reject) => {
                const xhr = new XMLHttpRequest();
                xhr.open('POST', `${API}/v1/admin/articles/upload`);
                xhr.withCredentials = true;
                xhr.upload.onprogress = (evt) => {
                    if (evt.lengthComputable)
                        setUploadPct(Math.round((evt.loaded / evt.total) * 100));
                };
                xhr.onload = () => {
                    if (xhr.status >= 200 && xhr.status < 300)
                        return resolve();
                    if (xhr.status === 409)
                        return reject(new Error('409'));
                    return reject(new Error(String(xhr.status)));
                };
                xhr.onerror = () => reject(new Error('network'));
                xhr.send(fd);
            });
        }
        catch (err) {
            const msg = String(err?.message ?? err ?? '');
            if (msg.includes('409')) {
                const ok = confirm('An article with the same ID already exists. Replace it?');
                if (!ok)
                    throw err;
                // resend with replace=true
                const fd2 = new FormData();
                if (title)
                    fd2.set('title', title);
                fd2.set('docx', docx);
                if (cover)
                    fd2.set('cover', cover);
                if (icon)
                    fd2.set('icon', icon);
                fd2.set('replace', 'true');
                await new Promise((resolve, reject) => {
                    const xhr = new XMLHttpRequest();
                    xhr.open('POST', `${API}/v1/admin/articles/upload`);
                    xhr.withCredentials = true;
                    xhr.upload.onprogress = (evt) => {
                        if (evt.lengthComputable)
                            setUploadPct(Math.round((evt.loaded / evt.total) * 100));
                    };
                    xhr.onload = () => {
                        if (xhr.status >= 200 && xhr.status < 300)
                            return resolve();
                        return reject(new Error(String(xhr.status)));
                    };
                    xhr.onerror = () => reject(new Error('network'));
                    xhr.send(fd2);
                });
            }
            else {
                throw err;
            }
            setTitle('');
            setDocx(null);
            setCover(null);
            setIcon(null);
            setUploadPct(0);
            const data = await api.get(`${API}/v1/admin/articles`);
            setArticles(data.items);
        }
        finally {
            setUploadBusy(false);
        }
    };
    const searchUsers = async (e) => { e.preventDefault(); const res = await api.get(`${API}/v1/admin/users/search?q=${encodeURIComponent(query)}`); setUsers(res.users); };
    // Auto-load latest users when switching to Users tab the first time
    useEffect(() => {
        if (auth !== 'yes')
            return;
        if (tab !== 'users')
            return;
        if (didInitialUserLoad)
            return;
        (async () => {
            try {
                const res = await api.get(`${API}/v1/admin/users/search?q=${encodeURIComponent(query)}`);
                setUsers(res.users);
            }
            catch {
                // ignore
            }
            finally {
                setDidInitialUserLoad(true);
            }
        })();
    }, [auth, tab, didInitialUserLoad, api, query]);
    const removeUser = async (id) => {
        if (!confirm('Are you sure you want to remove this user? This action cannot be undone.'))
            return;
        try {
            await api.del(`${API}/v1/admin/users/${id}`);
        }
        catch (err) {
            // If already deleted (404), treat as success; rethrow other errors
            const msg = String(err?.message ?? err ?? '');
            if (!msg.includes('404'))
                throw err;
        }
        // refresh total and current search results
        try {
            const sum = await api.get(`${API}/v1/admin/users/summary`);
            setUsersTotal(sum.total);
        }
        catch { }
        try {
            await searchUsers(new Event('submit'));
        }
        catch { }
    };
    if (auth === 'unknown')
        return _jsx(Box, { sx: { p: 3 }, children: _jsx(CircularProgress, {}) });
    if (auth === 'no')
        return _jsx(Login, { onDone: () => setAuth('yes') });
    return (_jsxs(Box, { sx: { display: 'grid', gridTemplateColumns: '280px 1fr', minHeight: '100dvh', overflowX: 'hidden' }, children: [_jsxs(Drawer, { variant: "permanent", sx: { width: 280, [`& .MuiDrawer-paper`]: { width: 280, boxSizing: 'border-box' } }, children: [_jsx(Toolbar, { children: _jsx(Typography, { variant: "h6", fontWeight: 700, children: "LWB Admin" }) }), _jsx(Divider, {}), _jsxs(List, { children: [_jsxs(ListItemButton, { selected: tab === 'articles', onClick: () => setTab('articles'), children: [_jsx(ListItemIcon, { children: _jsx(ArticleIcon, {}) }), _jsx(ListItemText, { primary: "Articles", secondary: "Manage content" })] }), _jsxs(ListItemButton, { selected: tab === 'users', onClick: () => setTab('users'), children: [_jsx(ListItemIcon, { children: _jsx(UsersIcon, {}) }), _jsx(ListItemText, { primary: "Users", secondary: "Manage accounts" })] })] }), _jsx(Box, { sx: { flexGrow: 1 } }), _jsx(Divider, {}), _jsxs(Stack, { direction: "row", alignItems: "center", justifyContent: "space-between", sx: { p: 2 }, children: [_jsx(Tooltip, { title: mode === 'dark' ? 'Switch to light' : 'Switch to dark', children: _jsx(IconButton, { onClick: toggle, color: "inherit", children: mode === 'dark' ? _jsx(LightModeIcon, {}) : _jsx(DarkModeIcon, {}) }) }), _jsx(Button, { onClick: logout, color: "error", startIcon: _jsx(LogoutIcon, {}), children: "Logout" })] })] }), _jsxs(Box, { component: "main", sx: { p: 3, overflowX: 'hidden' }, children: [_jsx(Toolbar, {}), tab === 'articles' && (_jsxs(Stack, { spacing: 3, children: [_jsx(Typography, { variant: "h5", fontWeight: 700, children: "Articles" }), _jsx(Card, { children: _jsx(CardContent, { children: _jsxs(Stack, { component: "form", onSubmit: upload, spacing: 2, sx: { maxWidth: 720 }, children: [_jsx(TextField, { label: "Title (optional)", value: title, onChange: (e) => setTitle(e.target.value), placeholder: "Defaults from docx", fullWidth: true }), _jsxs(Stack, { direction: { xs: 'column', sm: 'row' }, spacing: 2, children: [_jsxs(Button, { variant: "outlined", component: "label", startIcon: _jsx(UploadFileIcon, {}), children: ["Select DOCX", _jsx("input", { hidden: true, type: "file", accept: ".docx", onChange: e => setDocx(e.target.files?.[0] ?? null) })] }), _jsx(Typography, { sx: { alignSelf: 'center' }, color: "text.secondary", children: docx?.name ?? 'No file selected' })] }), _jsxs(Stack, { direction: { xs: 'column', sm: 'row' }, spacing: 2, children: [_jsxs(Button, { variant: "outlined", component: "label", children: ["Cover", _jsx("input", { hidden: true, type: "file", accept: "image/*", onChange: e => setCover(e.target.files?.[0] ?? null) })] }), _jsx(Typography, { sx: { alignSelf: 'center' }, color: "text.secondary", children: cover?.name ?? 'Optional' }), _jsxs(Button, { variant: "outlined", component: "label", children: ["Icon", _jsx("input", { hidden: true, type: "file", accept: "image/*", onChange: e => setIcon(e.target.files?.[0] ?? null) })] }), _jsx(Typography, { sx: { alignSelf: 'center' }, color: "text.secondary", children: icon?.name ?? 'Optional' })] }), uploadBusy && (_jsxs(Box, { children: [_jsx(LinearProgress, { variant: "determinate", value: uploadPct }), _jsxs(Typography, { variant: "caption", color: "text.secondary", children: [uploadPct, "%"] })] })), _jsx(Stack, { direction: "row", spacing: 1, children: _jsx(Button, { type: "submit", variant: "contained", disabled: uploadBusy || !docx, children: "Upload" }) })] }) }) }), _jsx(Grid, { container: true, spacing: 2, children: articles.slice().sort((a, b) => a.order - b.order).map(a => {
                                    const created = a.createdAt ? new Date(a.createdAt) : null;
                                    const updated = a.updatedAt ? new Date(a.updatedAt) : null;
                                    const publicLink = typeof a.publicPath === 'string' ? a.publicPath.replace(/^.*\/LWB\//, '/LWB/') : null;
                                    const bust = a.updatedAt ? (a.updatedAt.includes('?') ? a.updatedAt : encodeURIComponent(a.updatedAt)) : String(a.order);
                                    const coverSrc = a.cover ? `${a.cover}${a.cover.includes('?') ? '&' : '?'}v=${bust}` : null;
                                    const iconSrc = a.icon ? `${a.icon}${a.icon.includes('?') ? '&' : '?'}v=${bust}` : undefined;
                                    return (_jsx(Grid, { item: true, xs: 12, sm: 6, md: 4, lg: 3, children: _jsxs(Card, { sx: { height: '100%', display: 'flex', flexDirection: 'column' }, children: [_jsxs(Box, { sx: { position: 'relative' }, children: [coverSrc ? (_jsx(CardMedia, { component: "img", src: coverSrc, alt: "Cover", sx: { aspectRatio: '16/9', objectFit: 'cover' } })) : (_jsx(Box, { sx: { aspectRatio: '16/9', display: 'grid', placeItems: 'center', bgcolor: 'action.hover' }, children: _jsx(ArticleIcon, { sx: { fontSize: 48, color: 'text.secondary' } }) })), busyIds[a.id] && (_jsx(Box, { sx: { position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', bgcolor: 'rgba(0,0,0,0.25)' }, children: _jsx(CircularProgress, { size: 28 }) }))] }), _jsx(CardContent, { sx: { flexGrow: 1 }, children: _jsxs(Stack, { spacing: 1, children: [_jsxs(Stack, { direction: "row", spacing: 1, alignItems: "center", minWidth: 0, children: [iconSrc && _jsx(Avatar, { src: iconSrc, variant: "rounded", sx: { width: 28, height: 28 } }), _jsx(Typography, { variant: "subtitle1", fontWeight: 700, noWrap: true, title: a.title || a.filename, children: a.title || a.filename })] }), _jsx(Typography, { variant: "body2", color: "text.secondary", noWrap: true, title: a.filename, children: a.filename }), _jsxs(Stack, { direction: "row", spacing: 1, useFlexGap: true, flexWrap: "wrap", children: [_jsx(Chip, { size: "small", label: `Order #${a.order}` }), created && _jsx(Chip, { size: "small", variant: "outlined", label: `Created ${created.toLocaleDateString()}` }), updated && _jsx(Chip, { size: "small", variant: "outlined", label: `Updated ${updated.toLocaleDateString()}` })] }), publicLink && _jsx(MuiLink, { href: publicLink, target: "_blank", rel: "noreferrer", underline: "hover", children: "Open public link" })] }) }), _jsx(CardActions, { sx: { justifyContent: 'space-between' }, children: _jsxs(Stack, { direction: "row", spacing: 1, children: [_jsx(Tooltip, { title: "Move up", children: _jsx("span", { children: _jsx(IconButton, { size: "small", onClick: () => move(a.id, 'up'), children: _jsx(ArrowUpwardIcon, { fontSize: "inherit" }) }) }) }), _jsx(Tooltip, { title: "Move down", children: _jsx("span", { children: _jsx(IconButton, { size: "small", onClick: () => move(a.id, 'down'), children: _jsx(ArrowDownwardIcon, { fontSize: "inherit" }) }) }) }), _jsx(Tooltip, { title: "Edit title", children: _jsx("span", { children: _jsx(IconButton, { size: "small", onClick: () => { const t = prompt('New title', a.title); if (t !== null)
                                                                            edit(a.id, t); }, children: _jsx(EditIcon, { fontSize: "inherit" }) }) }) }), _jsx(Tooltip, { title: "Change cover", children: _jsx("span", { children: _jsx(IconButton, { size: "small", onClick: () => changeCover(a.id), disabled: !!busyIds[a.id], children: _jsx(ImageIcon, { fontSize: "inherit" }) }) }) }), _jsx(Tooltip, { title: "Change icon", children: _jsx("span", { children: _jsx(IconButton, { size: "small", onClick: () => changeIcon(a.id), disabled: !!busyIds[a.id], children: _jsx(InsertPhotoIcon, { fontSize: "inherit" }) }) }) })] }) }), _jsx(Snackbar, { open: toast.open, autoHideDuration: 2500, onClose: () => setToast(t => ({ ...t, open: false })), anchorOrigin: { vertical: 'bottom', horizontal: 'center' }, children: _jsx(Alert, { onClose: () => setToast(t => ({ ...t, open: false })), severity: toast.severity, sx: { width: '100%' }, children: toast.message }) })] }) }, a.id));
                                }) })] })), tab === 'users' && (_jsxs(Stack, { spacing: 2, children: [_jsx(Typography, { variant: "h5", fontWeight: 700, children: "Users" }), _jsxs(Typography, { color: "text.secondary", children: ["Total registered users: ", usersTotal] }), _jsxs(Stack, { component: "form", onSubmit: searchUsers, direction: { xs: 'column', sm: 'row' }, spacing: 1, alignItems: { sm: 'center' }, children: [_jsx(TextField, { value: query, onChange: e => setQuery(e.target.value), placeholder: "Search username", size: "small" }), _jsx(Button, { type: "submit", variant: "contained", startIcon: _jsx(SearchIcon, {}), children: "Search" }), _jsx(Typography, { variant: "caption", color: "text.secondary", children: "Tip: Leave empty and click Search to list latest users." })] }), _jsx(DataGrid, { autoHeight: true, disableRowSelectionOnClick: true, rows: users, getRowId: (r) => r.id, columns: [
                { field: 'username', headerName: 'Username', flex: 1, minWidth: 180 },
                { field: 'createdAt', headerName: 'Registered', minWidth: 160, valueGetter: (p) => (p == null ? void 0 : p.row) == null ? void 0 : p.row.createdAt, renderCell: (p) => {
                        const v = p == null ? void 0 : p.row.createdAt;
                        if (!v)
                            return '-';
                        const d = new Date(v);
                        return isNaN(d.getTime()) ? '-' : d.toLocaleDateString();
                    } },
                { field: 'bookmarks', headerName: 'Bookmarks', width: 120, valueGetter: (p) => {
                        const v = p == null ? void 0 : p.row.bookmarks;
                        return v ?? '-';
                    } },
                { field: 'discussions', headerName: 'Threads', width: 120, valueGetter: (p) => {
                        const v = p == null ? void 0 : p.row.discussions;
                        return v ?? '-';
                    } },
                { field: 'lastLogin', headerName: 'Last login', minWidth: 180, valueGetter: (p) => (p == null ? void 0 : p.row) == null ? void 0 : p.row.lastLogin, renderCell: (p) => {
                        const v = p == null ? void 0 : p.row.lastLogin;
                        if (!v)
                            return '-';
                        const d = new Date(v);
                        return isNaN(d.getTime()) ? '-' : d.toLocaleString();
                    } },
                { field: 'actions', headerName: '', sortable: false, width: 120, renderCell: (p) => (_jsx(Button, { size: "small", color: "error", startIcon: _jsx(DeleteIcon, {}), onClick: () => removeUser(p.row.id), children: "Remove" })) },
            ], pageSizeOptions: [10, 25, 50], initialState: { pagination: { paginationModel: { pageSize: 10, page: 0 } } } })] }))] })] }));
}
