import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
const API = import.meta.env.VITE_API_URL ?? 'http://localhost:5050';
export default function App() {
    const [queue, setQueue] = useState(null);
    const [users, setUsers] = useState([]);
    const [error, setError] = useState(null);
    useEffect(() => {
        Promise.all([
            fetch(`${API}/v1/admin/ingestion/queue`).then(r => r.json()),
            fetch(`${API}/v1/admin/support/users`).then(r => r.json())
        ])
            .then(([q, u]) => { setQueue(q); setUsers(u); })
            .catch(e => setError(String(e)));
    }, []);
    return (_jsxs("div", { style: { padding: 16, fontFamily: 'Inter, system-ui, Arial' }, children: [_jsx("h1", { children: "LWB Admin" }), error && _jsx("p", { style: { color: 'crimson' }, children: error }), _jsxs("section", { children: [_jsx("h2", { children: "Ingestion Queue" }), !queue ? (_jsx("p", { children: "Loading\u2026" })) : (_jsxs("div", { children: [_jsxs("p", { children: ["Pending: ", queue.pending, " | Running: ", queue.running, " | Failed: ", queue.failed] }), _jsxs("table", { cellPadding: 6, children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "ID" }), _jsx("th", { children: "Article" }), _jsx("th", { children: "Status" }), _jsx("th", { children: "Started" })] }) }), _jsx("tbody", { children: queue.items.map(it => (_jsxs("tr", { children: [_jsx("td", { children: it.id }), _jsx("td", { children: it.article }), _jsx("td", { children: it.status }), _jsx("td", { children: it.startedAt ?? '-' })] }, it.id))) })] })] }))] }), _jsxs("section", { children: [_jsx("h2", { children: "User Support" }), !users.length ? _jsx("p", { children: "No users" }) : (_jsx("ul", { children: users.map(u => (_jsxs("li", { children: [u.email, " \u2014 issues: ", u.issues] }, u.id))) }))] })] }));
}
