import { PREF_KEYS, loadPref, savePref } from '../core/state.js'

export function initSidebar(){
  const sidebar = document.getElementById('sidebar')
  const collapseBtn = document.getElementById('sidebar-collapse')
  const layout = document.querySelector('.layout')
  if(!sidebar || !collapseBtn || !layout) return

  function apply(collapsed){
    if(collapsed){
      sidebar.setAttribute('data-collapsed','true')
      collapseBtn.setAttribute('aria-expanded','false')
      layout.classList.add('is-collapsed')
      savePref(PREF_KEYS.sidebar,'collapsed')
    } else {
      sidebar.removeAttribute('data-collapsed')
      collapseBtn.setAttribute('aria-expanded','true')
      layout.classList.remove('is-collapsed')
      savePref(PREF_KEYS.sidebar,'expanded')
    }
  }

  const pref = loadPref(PREF_KEYS.sidebar, 'expanded')
  apply(pref === 'collapsed')

  collapseBtn.addEventListener('click', () => {
    const collapsed = sidebar.hasAttribute('data-collapsed')
    apply(!collapsed)
  })

  document.addEventListener('click', (e) => {
    if(window.innerWidth > 640) return
    if(!sidebar.hasAttribute('data-collapsed')){
      const target = e.target
      if(target instanceof Node && !sidebar.contains(target) && !collapseBtn.contains(target)){
        apply(true)
      }
    }
  })
}
