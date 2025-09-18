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
      // Snapshot current computed background styles (including ::before gradient) before theme flip
      const csSidebar = getComputedStyle(sidebar)
      const csBefore = getComputedStyle(sidebar, '::before')
      const bgColor = csSidebar.backgroundColor || 'transparent'
      const beforeImg = csBefore.backgroundImage && csBefore.backgroundImage !== 'none' ? csBefore.backgroundImage : ''
      // Combine gradient(s) + base color for the layer
      // If multiple gradients exist they are comma separated already.
      if(beforeImg){
        fadeLayer.style.backgroundImage = beforeImg
      }
      fadeLayer.style.backgroundColor = bgColor
      // Match blend/opacity feel of ::before
      fadeLayer.style.opacity = csBefore.opacity || '1'
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
