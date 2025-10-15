export async function loadTemplate(url){
  const res = await fetch(url, { cache: 'no-store' })
  if(!res.ok) throw new Error(`Failed to load template: ${url}`)
  return await res.text()
}

export function renderTemplate(tpl, data){
  let out = tpl
  for(const [k,v] of Object.entries(data)){
    const safe = String(v ?? '')
    const re = new RegExp(`\\{\\{\\s*${escapeRegExp(k)}\\s*\\}\\}`,'g')
    out = out.replace(re, safe)
  }
  return out
}

function escapeRegExp(s){
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
