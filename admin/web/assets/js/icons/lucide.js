// Lucide icon loading & refresh utilities
let lucideLoaded = false

export async function loadIcons(){
  if(lucideLoaded || window.__LUCIDE_LOADED__) return
  window.__LUCIDE_LOADED__ = true
  try {
    const mod = await import('https://cdn.jsdelivr.net/npm/lucide@0.469.0/+esm')
    const { createIcons, icons } = mod
    createIcons({ icons })
    lucideLoaded = true
  } catch(e){
    console.warn('Lucide failed to load', e)
  }
}

export async function refreshIcons(){
  try {
    const mod = await import('https://cdn.jsdelivr.net/npm/lucide@0.469.0/+esm')
    const { createIcons, icons } = mod
    createIcons({ icons })
  } catch(e){ /* silent */ }
}

export function setThemeIcon(){
  const holder = document.querySelector('[data-icon-theme]')
  if(!holder) return
  const isLight = document.documentElement.getAttribute('data-theme') === 'light'
  holder.setAttribute('data-lucide', isLight ? 'moon' : 'sun')
  refreshIcons()
}
