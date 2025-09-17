import { saveToken, loadToken, clearToken, state } from '../core/state.js'
import { api } from '../core/api.js'
import { render } from '../router/router.js'

export function initAuth(){
  loadToken()
  const loginOverlay = document.getElementById('login-overlay')
  const loginForm = document.getElementById('login-form')
  const loginError = document.getElementById('login-error')

  async function ensureAuth(){
    if (!state.token) { loginOverlay.hidden = false; loginOverlay.className='overlay'; return false }
    loginOverlay.hidden = true; loginOverlay.className=''; return true
  }

  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault()
    loginError.textContent = ''
    const fd = new FormData(loginForm)
    const body = JSON.stringify(Object.fromEntries(fd.entries()))
    try{
      const res = await api('/login', { method:'POST', body, headers: {'Content-Type':'application/json'}, })
      const json = await res.json()
      saveToken(json.token)
      await render(state.view)
      await ensureAuth()
    }catch(err){ loginError.textContent = 'Login failed' }
  })

  document.getElementById('logout').addEventListener('click', () => { clearToken(); render(state.view); loginOverlay.hidden=false; loginOverlay.className='overlay' })

  return { ensureAuth }
}
