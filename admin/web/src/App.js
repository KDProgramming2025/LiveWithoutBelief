import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useMemo, useState } from 'react';
const API = import.meta.env.VITE_API_URL ?? `${location.origin}/LWB/Admin/api`;
function useJson() {
    return useMemo(() => ({
        async get(url) { const r = await fetch(url, { credentials: 'include' }); if (!r.ok)
            throw new Error(`${r.status}`); return r.json(); },
        async post(url, body) { const r = await fetch(url, { method: 'POST', headers: body instanceof FormData ? undefined : { 'Content-Type': 'application/json' }, body: body instanceof FormData ? body : JSON.stringify(body ?? {}), credentials: 'include' }); if (!r.ok)
            throw new Error(`${r.status}`); return r.json(); },
        async del(url) { const r = await fetch(url, { method: 'DELETE', credentials: 'include' }); if (!r.ok)
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
    return (_jsx("div", { style: { display: 'grid', placeItems: 'center', minHeight: '100dvh', fontFamily: 'Inter, system-ui, Arial' }, children: _jsxs("form", { onSubmit: submit, style: { width: 340, padding: 24, border: '1px solid #ddd', borderRadius: 12, boxShadow: '0 2px 12px rgba(0,0,0,0.06)', background: '#fff' }, children: [_jsx("h1", { style: { marginTop: 0 }, children: "LWB Admin" }), err && _jsx("div", { style: { color: 'crimson', marginBottom: 8 }, children: err }), _jsxs("label", { children: ["Username", _jsx("input", { value: username, onChange: e => setUsername(e.target.value), required: true, style: { width: '100%', margin: '6px 0 12px', padding: 8 } })] }), _jsxs("label", { children: ["Password", _jsx("input", { type: "password", value: password, onChange: e => setPassword(e.target.value), required: true, style: { width: '100%', margin: '6px 0 16px', padding: 8 } })] }), _jsx("button", { type: "submit", style: { width: '100%', padding: 10 }, children: "Login" })] }) }));
}
export default function App() {
    const api = useJson();
    const [auth, setAuth] = useState('unknown');
    const [tab, setTab] = useState('articles');
    // Article state
    const [articles, setArticles] = useState([]);
    const [uploadBusy, setUploadBusy] = useState(false);
    const [title, setTitle] = useState('');
    const [docx, setDocx] = useState(null);
    const [cover, setCover] = useState(null);
    const [icon, setIcon] = useState(null);
    // Users state
    const [usersTotal, setUsersTotal] = useState(0);
    const [query, setQuery] = useState('');
    const [users, setUsers] = useState([]);
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
    const edit = async (id, newTitle) => {
        const fd = new FormData();
        if (newTitle)
            fd.set('title', newTitle);
        await api.post(`${API}/v1/admin/articles/${id}/edit`, fd);
        const data = await api.get(`${API}/v1/admin/articles`);
        setArticles(data.items);
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
            await api.post(`${API}/v1/admin/articles/upload`, fd);
            setTitle('');
            setDocx(null);
            setCover(null);
            setIcon(null);
            const data = await api.get(`${API}/v1/admin/articles`);
            setArticles(data.items);
        }
        finally {
            setUploadBusy(false);
        }
    };
    const searchUsers = async (e) => { e.preventDefault(); const res = await api.get(`${API}/v1/admin/users/search?q=${encodeURIComponent(query)}`); setUsers(res.users); };
    const removeUser = async (id) => { await api.del(`${API}/v1/admin/users/${id}`); await searchUsers(new Event('submit')); };
    if (auth === 'unknown')
        return _jsx("div", { style: { padding: 16 }, children: "Loading\u2026" });
    if (auth === 'no')
        return _jsx(Login, { onDone: () => setAuth('yes') });
    return (_jsxs("div", { style: { display: 'grid', gridTemplateColumns: '240px 1fr', minHeight: '100dvh', fontFamily: 'Inter, system-ui, Arial' }, children: [_jsxs("aside", { style: { borderRight: '1px solid #eee', padding: 16 }, children: [_jsx("h2", { style: { marginTop: 0 }, children: "LWB Admin" }), _jsxs("nav", { style: { display: 'grid', gap: 8 }, children: [_jsx("button", { onClick: () => setTab('articles'), style: { textAlign: 'left', padding: 8, background: tab === 'articles' ? '#f0f0f0' : 'transparent' }, children: "Article management" }), _jsx("button", { onClick: () => setTab('users'), style: { textAlign: 'left', padding: 8, background: tab === 'users' ? '#f0f0f0' : 'transparent' }, children: "User Management" }), _jsx("button", { onClick: logout, style: { textAlign: 'left', padding: 8, color: 'crimson' }, children: "Logout" })] })] }), _jsxs("main", { style: { padding: 24 }, children: [tab === 'articles' && (_jsxs("div", { children: [_jsx("h2", { children: "Articles" }), _jsxs("form", { onSubmit: upload, style: { display: 'grid', gap: 8, maxWidth: 640, padding: 12, border: '1px solid #ddd', borderRadius: 8 }, children: [_jsx("div", { children: _jsxs("label", { children: ["Title", _jsx("br", {}), _jsx("input", { value: title, onChange: e => setTitle(e.target.value), placeholder: "Optional; defaults from docx", style: { width: '100%', padding: 8 } })] }) }), _jsx("div", { children: _jsxs("label", { children: ["DOCX", _jsx("br", {}), _jsx("input", { type: "file", accept: ".docx", onChange: e => setDocx(e.target.files?.[0] ?? null), required: true })] }) }), _jsxs("div", { style: { display: 'flex', gap: 12 }, children: [_jsxs("label", { children: ["Cover", _jsx("br", {}), _jsx("input", { type: "file", accept: "image/*", onChange: e => setCover(e.target.files?.[0] ?? null) })] }), _jsxs("label", { children: ["Icon", _jsx("br", {}), _jsx("input", { type: "file", accept: "image/*", onChange: e => setIcon(e.target.files?.[0] ?? null) })] })] }), _jsx("div", { children: _jsx("button", { type: "submit", disabled: uploadBusy, children: "Upload" }) })] }), _jsxs("table", { cellPadding: 6, style: { marginTop: 16, borderCollapse: 'collapse' }, children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Order" }), _jsx("th", { children: "Title" }), _jsx("th", { children: "Filename" }), _jsx("th", { children: "Created" }), _jsx("th", { children: "Updated" }), _jsx("th", { children: "Cover" }), _jsx("th", { children: "Icon" }), _jsx("th", { children: "Path" }), _jsx("th", { children: "Actions" })] }) }), _jsx("tbody", { children: articles.slice().sort((a, b) => a.order - b.order).map(a => (_jsxs("tr", { children: [_jsx("td", { children: a.order }), _jsx("td", { children: a.title }), _jsx("td", { children: a.filename }), _jsx("td", { children: new Date(a.createdAt).toLocaleString() }), _jsx("td", { children: new Date(a.updatedAt).toLocaleString() }), _jsx("td", { children: a.cover || '-' }), _jsx("td", { children: a.icon || '-' }), _jsx("td", { children: a.publicPath.replace(/^.*\/LWB\//, '/LWB/') }), _jsxs("td", { children: [_jsx("button", { onClick: () => move(a.id, 'up'), children: "Up" }), ' ', _jsx("button", { onClick: () => move(a.id, 'down'), children: "Down" }), ' ', _jsx("button", { onClick: () => { const t = prompt('New title', a.title); if (t !== null)
                                                                edit(a.id, t); }, children: "Edit title" })] })] }, a.id))) })] })] })), tab === 'users' && (_jsxs("div", { children: [_jsx("h2", { children: "Users" }), _jsxs("p", { children: ["Total registered users: ", usersTotal] }), _jsxs("form", { onSubmit: searchUsers, style: { display: 'flex', gap: 8 }, children: [_jsx("input", { value: query, onChange: e => setQuery(e.target.value), placeholder: "Search username", style: { padding: 8 } }), _jsx("button", { type: "submit", children: "Search" })] }), _jsxs("table", { cellPadding: 6, style: { marginTop: 12 }, children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Username" }), _jsx("th", { children: "Registered" }), _jsx("th", { children: "Bookmarks" }), _jsx("th", { children: "Threads" }), _jsx("th", { children: "Last login" }), _jsx("th", {})] }) }), _jsx("tbody", { children: users.map(u => (_jsxs("tr", { children: [_jsx("td", { children: u.username }), _jsx("td", { children: new Date(u.createdAt).toLocaleDateString() }), _jsx("td", { children: u.bookmarks ?? '-' }), _jsx("td", { children: u.discussions ?? '-' }), _jsx("td", { children: u.lastLogin ? new Date(u.lastLogin).toLocaleString() : '-' }), _jsx("td", { children: _jsx("button", { onClick: () => removeUser(u.id), style: { color: 'crimson' }, children: "Remove" }) })] }, u.id))) })] })] }))] })] }));
}
