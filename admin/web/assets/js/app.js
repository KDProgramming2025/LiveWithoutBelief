import { initSidebar } from './ui/sidebar.js'
import { initAuth } from './auth/auth.js'
import { loadIcons } from './icons/lucide.js'
import { render } from './router/router.js'
import { state } from './core/state.js'

function bindNav(){
  document.getElementById('nav-menu').addEventListener('click', () => render('menu'))
  document.getElementById('nav-articles').addEventListener('click', () => render('articles'))
  document.getElementById('nav-users').addEventListener('click', () => render('users'))
}

async function boot(){
  initSidebar()
  const { ensureAuth } = initAuth()
  bindNav()
  await loadIcons()
  render(state.view).then(ensureAuth)
}

document.addEventListener('DOMContentLoaded', boot)

// re-export helpers for potential debugging
export { state } from './core/state.js'
