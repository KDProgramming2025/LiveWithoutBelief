import { PREF_KEYS, loadPref, savePref } from '../core/state.js'
import { refreshIcons } from '../icons/lucide.js'

export function initSidebar(){
  const sidebar = document.getElementById('sidebar')
  const collapseBtn = document.getElementById('sidebar-collapse')
  const layoutRoot = document.querySelector('.layout')
  // Helper: get the current icon node (supports original span or generated svg)
  const getIconEl = () => collapseBtn?.querySelector('[data-lucide], svg.lucide')
  const setIcon = (collapsed) => {
    const el = getIconEl()
    if(!el) return
    el.setAttribute('data-lucide', collapsed ? 'chevrons-right' : 'chevrons-left')
    refreshIcons()
  }

  const storedSidebar = loadPref(PREF_KEYS.sidebar, 'expanded')
  if(storedSidebar === 'collapsed'){
    sidebar?.setAttribute('data-state','collapsed')
    collapseBtn?.setAttribute('aria-expanded','false')
    layoutRoot?.classList.add('sidebar--collapsed')
    setIcon(true)
  }

  collapseBtn?.addEventListener('click', () => {
    const collapsed = sidebar?.getAttribute('data-state') === 'collapsed'
    if(collapsed){
      sidebar?.classList.add('is-expanding')
      sidebar?.setAttribute('data-state','expanded')
      collapseBtn.setAttribute('aria-expanded','true')
      layoutRoot?.classList.remove('sidebar--collapsed')
      savePref(PREF_KEYS.sidebar,'expanded')
      setIcon(false)
      const onEnd = (e) => {
        if(e.propertyName === 'width'){
          sidebar?.classList.remove('is-expanding')
          sidebar?.removeEventListener('transitionend', onEnd)
        }
      }
      sidebar?.addEventListener('transitionend', onEnd)
    } else {
      sidebar?.classList.add('is-collapsing')
      sidebar?.setAttribute('data-state','collapsed')
      collapseBtn.setAttribute('aria-expanded','false')
      layoutRoot?.classList.add('sidebar--collapsed')
      savePref(PREF_KEYS.sidebar,'collapsed')
      setIcon(true)
      const onEnd = (e) => {
        if(e.propertyName === 'width'){
          sidebar?.classList.remove('is-collapsing')
          sidebar?.removeEventListener('transitionend', onEnd)
        }
      }
      sidebar?.addEventListener('transitionend', onEnd)
    }
  })

  // Close expanded sidebar on very small screens when user clicks outside
  document.addEventListener('click', (e) => {
    if(window.innerWidth > 640) return
    if(!sidebar) return
    if(sidebar.getAttribute('data-state') !== 'expanded') return
    const target = e.target
    if(target instanceof Node && !sidebar.contains(target) && !collapseBtn.contains(target)){
      sidebar.setAttribute('data-state','collapsed')
      collapseBtn.setAttribute('aria-expanded','false')
      layoutRoot?.classList.add('sidebar--collapsed')
      savePref(PREF_KEYS.sidebar,'collapsed')
      setIcon(true)
    }
  })
}
