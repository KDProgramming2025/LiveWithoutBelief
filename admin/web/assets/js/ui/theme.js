import { PREF_KEYS, loadPref, savePref } from '../core/state.js'
import { setThemeIcon } from '../icons/lucide.js'

export function initTheme(){
  const html = document.documentElement
  const themeToggle = document.getElementById('theme-toggle')
  const sidebar = document.querySelector('.sidebar')
  const storedTheme = loadPref(PREF_KEYS.theme, 'dark')
  if(storedTheme === 'light') html.setAttribute('data-theme','light')
  else html.removeAttribute('data-theme')
  if(themeToggle) themeToggle.setAttribute('aria-pressed', storedTheme === 'light')
  themeToggle?.addEventListener('click', () => {
    // Create a fading overlay layer inside sidebar using the CURRENT theme token values
    // so that when we switch the underlying tokens, the old appearance gently fades out.
    let fadeLayer
    if(sidebar){
      const rootStyles = getComputedStyle(document.documentElement)
      const panel = rootStyles.getPropertyValue('--panel').trim()
      const panelAlt = rootStyles.getPropertyValue('--panel-alt').trim() || panel
      const accentRgb = rootStyles.getPropertyValue('--accent-rgb').trim() || '56 189 248'
      const gradient = `linear-gradient(180deg, ${panel} 0%, ${panelAlt} 120%), radial-gradient(circle at 40% 0%, rgba(${accentRgb}/0.18), transparent 60%)`
      fadeLayer = document.createElement('div')
      fadeLayer.className = 'sidebar-fade-layer'
      fadeLayer.style.backgroundImage = gradient
      fadeLayer.style.backgroundColor = panel
      sidebar.appendChild(fadeLayer)
      // Force a reflow so the browser registers initial opacity before we start fading
      // eslint-disable-next-line @typescript-eslint/no-unused-expressions
      fadeLayer.offsetHeight
    }
    const isLight = html.getAttribute('data-theme') === 'light'
    if(isLight){ html.removeAttribute('data-theme'); savePref(PREF_KEYS.theme,'dark'); themeToggle.setAttribute('aria-pressed','false') }
    else { html.setAttribute('data-theme','light'); savePref(PREF_KEYS.theme,'light'); themeToggle.setAttribute('aria-pressed','true') }
    setThemeIcon()
    if(fadeLayer){
      requestAnimationFrame(()=>{
        fadeLayer.classList.add('is-fading')
        setTimeout(()=>fadeLayer.remove(),1000) // match 1s ease-in
      })
    }
  })
  // Set icon immediately
  setThemeIcon()
}
