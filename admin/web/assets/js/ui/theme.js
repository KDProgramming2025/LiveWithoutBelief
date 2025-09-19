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
    // Create a fading overlay layer inside sidebar for cross-fade of background
    let fadeLayer
    if(sidebar){
      fadeLayer = document.createElement('div')
      fadeLayer.className = 'sidebar-fade-layer'
      // snapshot current computed gradient/color using existing ::before by cloning sidebar before change
      // Instead of cloning pseudo, we approximate by using getComputedStyle for background-image
      const cs = getComputedStyle(sidebar)
      fadeLayer.style.backgroundImage = cs.backgroundImage || ''
      fadeLayer.style.backgroundColor = cs.backgroundColor || 'transparent'
      sidebar.appendChild(fadeLayer)
      // Force reflow then animate opacity to 0 after theme switch
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
