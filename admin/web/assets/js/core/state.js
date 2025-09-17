// Global application state and tokens
export const state = {
  token: null,
  view: 'menu'
}

export const PREF_KEYS = Object.freeze({
  theme: 'lwb_admin_theme',
  sidebar: 'lwb_admin_sidebar'
})

export function savePref(key, val){
  try { localStorage.setItem(key, val) } catch {}
}
export function loadPref(key, fallback){
  try { return localStorage.getItem(key) ?? fallback } catch { return fallback }
}

export function saveToken(t){
  localStorage.setItem('lwb_admin_token', t)
  state.token = t
}
export function loadToken(){
  state.token = localStorage.getItem('lwb_admin_token')
}
export function clearToken(){
  localStorage.removeItem('lwb_admin_token')
  state.token = null
}
