import { initSidebar } from './ui/sidebar.js'
import { initAuth } from './auth/auth.js'
import { loadIcons } from './icons/lucide.js'
import { render } from './router/router.js'
import { state, PREF_KEYS, loadPref, savePref } from './core/state.js'

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
  // Restore last view
  try{
    const lastView = loadPref(PREF_KEYS.view, state.view)
    if(lastView && ['menu','articles','users'].includes(lastView)) state.view = lastView
  }catch{}
  // Persist scroll position per view
  window.addEventListener('scroll', () => {
    try{
      const raw = loadPref(PREF_KEYS.scroll, '{}')
      const map = JSON.parse(raw || '{}')
      map[state.view] = window.scrollY || 0
      savePref(PREF_KEYS.scroll, JSON.stringify(map))
    }catch{}
  }, { passive: true })
  render(state.view).then(ensureAuth)
}

document.addEventListener('DOMContentLoaded', boot)

// re-export helpers for potential debugging
export { state } from './core/state.js'
