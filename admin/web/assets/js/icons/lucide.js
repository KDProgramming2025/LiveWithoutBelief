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

// Theme icon removed — single dark theme only
