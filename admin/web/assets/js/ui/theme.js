import { PREF_KEYS, loadPref, savePref } from '../core/state.js'
import { setThemeIcon } from '../icons/lucide.js'

export function initTheme(){
  const html = document.documentElement
  const themeToggle = document.getElementById('theme-toggle')
  const storedTheme = loadPref(PREF_KEYS.theme, 'dark')
  if(storedTheme === 'light') html.setAttribute('data-theme','light')
  else html.removeAttribute('data-theme')
  if(themeToggle) themeToggle.setAttribute('aria-pressed', storedTheme === 'light')
  themeToggle?.addEventListener('click', () => {
    const isLight = html.getAttribute('data-theme') === 'light'
    if(isLight){ html.removeAttribute('data-theme'); savePref(PREF_KEYS.theme,'dark'); themeToggle.setAttribute('aria-pressed','false') }
    else { html.setAttribute('data-theme','light'); savePref(PREF_KEYS.theme,'light'); themeToggle.setAttribute('aria-pressed','true') }
    setThemeIcon()
  })
  // Set icon immediately
  setThemeIcon()
}
