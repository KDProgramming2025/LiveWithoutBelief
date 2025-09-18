// Formatting helpers
export function fmtBytes(bytes){
  if(!isFinite(bytes) || bytes < 0) return '0 B'
  const units = ['B','KB','MB','GB','TB']
  let i = 0
  let val = bytes
  while(val >= 1024 && i < units.length - 1){ val /= 1024; i++ }
  return (i === 0 ? Math.round(val) : val.toFixed(1)) + ' ' + units[i]
}
export function fmtEta(sec){
  sec = Math.max(0, Number(sec) || 0)
  const h = Math.floor(sec/3600)
  const m = Math.floor((sec%3600)/60)
  const s = sec%60
  const pad = (n) => String(n).padStart(2,'0')
  if(h>0) return `${h}:${pad(m)}:${pad(s)}`
  return `${m}:${pad(s)}`
}

export function fmtLocalDateTime(input){
  if(!input) return ''
  try{
    const d = new Date(input)
    if (isNaN(d.getTime())) return ''
    const yyyy = d.getFullYear()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mi = String(d.getMinutes()).padStart(2, '0')
    const ss = String(d.getSeconds()).padStart(2, '0')
    return `${yyyy}/${mm}/${dd} ${hh}:${mi}:${ss}`
  } catch { return '' }
}
