import { state, clearToken } from './state.js'

export async function api(path, opts = {}) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {})
  if (state.token) headers['Authorization'] = `Bearer ${state.token}`
  const res = await fetch(`/v1/admin${path}`, { ...opts, headers })
  if (res.status === 401) {
    // Auto-logout and show login overlay on auth expiry
    clearToken()
    const ov = document.getElementById('login-overlay')
    if (ov) { ov.hidden = false; ov.className = 'overlay' }
    throw new Error('unauthorized')
  }
  return res
}
