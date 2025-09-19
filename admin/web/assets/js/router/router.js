import { state, PREF_KEYS, savePref, loadPref } from '../core/state.js'
import { viewMenu } from '../views/menu.js'
import { viewArticles } from '../views/articles.js'
import { viewUsers } from '../views/users.js'
import { refreshIcons } from '../icons/lucide.js'

const registry = {
  menu: viewMenu,
  articles: viewArticles,
  users: viewUsers
}

export async function render(view){
  state.view = view
  // Persist active view
  savePref(PREF_KEYS.view, view)
  // Persist current scroll for previous view (if any)
  try{
    const raw = loadPref(PREF_KEYS.scroll, '{}')
    const map = JSON.parse(raw || '{}')
    map[view] = 0 // reset destination view scroll on render
    savePref(PREF_KEYS.scroll, JSON.stringify(map))
  }catch{}
  document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === view))
  const content = document.getElementById('content')
  content.classList.toggle('content--wide', view === 'articles')
  content.innerHTML = ''
  content.appendChild(await registry[view]())
  // Re-apply Lucide icons for any newly injected [data-lucide] nodes
  await refreshIcons()
  // Restore scroll position for this view if present
  try{
    const raw = loadPref(PREF_KEYS.scroll, '{}')
    const map = JSON.parse(raw || '{}')
    const y = Number(map[view] || 0)
    if(Number.isFinite(y) && y > 0){ window.scrollTo({ top: y, behavior: 'instant' }) }
  }catch{}
}
