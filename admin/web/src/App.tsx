import { useEffect, useState } from 'react'

type QueueItem = { id: string; article: string; status: string; startedAt?: string }

type QueueResponse = {
  pending: number
  running: number
  failed: number
  items: QueueItem[]
}

type UserSupport = { id: string; email: string; issues: number }

// Prefer same-origin base when hosted under /LWB/Admin; fallback to localhost for dev
const API = import.meta.env.VITE_API_URL ?? `${location.origin}`

export default function App() {
  const [queue, setQueue] = useState<QueueResponse | null>(null)
  const [users, setUsers] = useState<UserSupport[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      fetch(`${API}/v1/admin/ingestion/queue`).then(r => r.json()),
      fetch(`${API}/v1/admin/support/users`).then(r => r.json())
    ])
      .then(([q, u]) => { setQueue(q); setUsers(u) })
      .catch(e => setError(String(e)))
  }, [])

  return (
    <div style={{ padding: 16, fontFamily: 'Inter, system-ui, Arial' }}>
      <h1>LWB Admin</h1>
      {error && <p style={{ color: 'crimson' }}>{error}</p>}

      <section>
        <h2>Ingestion Queue</h2>
        {!queue ? (
          <p>Loading…</p>
        ) : (
          <div>
            <p>Pending: {queue.pending} | Running: {queue.running} | Failed: {queue.failed}</p>
            <table cellPadding={6}>
              <thead>
                <tr><th>ID</th><th>Article</th><th>Status</th><th>Started</th></tr>
              </thead>
              <tbody>
                {queue.items.map(it => (
                  <tr key={it.id}>
                    <td>{it.id}</td>
                    <td>{it.article}</td>
                    <td>{it.status}</td>
                    <td>{it.startedAt ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section>
        <h2>User Support</h2>
        {!users.length ? <p>No users</p> : (
          <ul>
            {users.map(u => (
              <li key={u.id}>{u.email} — issues: {u.issues}</li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
