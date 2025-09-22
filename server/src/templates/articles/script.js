// Enhance YouTube links into embeds
document.addEventListener('DOMContentLoaded', () => {
  const anchors = Array.from(document.querySelectorAll('a[href]'))
  for(const a of anchors){
    const href = a.getAttribute('href') || ''
    const vid = extractYouTubeId(href)
    if(!vid) continue
    const iframe = document.createElement('iframe')
    iframe.width = '560'; iframe.height = '315'
    iframe.src = 'https://www.youtube.com/embed/' + vid
    iframe.title = 'YouTube video player'
    iframe.frameBorder = '0'
    iframe.allow = 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share'
    iframe.allowFullscreen = true
    const wrap = document.createElement('div')
    wrap.style.position = 'relative'; wrap.style.paddingBottom = '56.25%'; wrap.style.height = '0'; wrap.style.margin = '12px 0'
    iframe.style.position = 'absolute'; iframe.style.top = '0'; iframe.style.left = '0'; iframe.style.width = '100%'; iframe.style.height = '100%'
    wrap.appendChild(iframe)
    a.insertAdjacentElement('afterend', wrap)
  }
  function extractYouTubeId(u){
    try{
      const url = new URL(u)
      if(url.hostname.includes('youtube.com')) return url.searchParams.get('v')
      if(url.hostname.includes('youtu.be')) return url.pathname.replace(/^[/]/,'')
    }catch{}
    return null
  }

  // Minimal YouTube overlay handler for embedded iframes in template
  const ytEmbeds = Array.from(document.querySelectorAll('.media__item.youtube .yt-wrap'))
  ytEmbeds.forEach(wrap => {
    const iframe = wrap.querySelector('iframe')
    const overlay = wrap.querySelector('.yt-overlay')
    const btn = overlay && overlay.querySelector('[data-yt-open]')
    if(!iframe) return
    // Open on YouTube button
    if(btn){
      const id = (iframe.src.match(/[?&]v=([^&]+)/) || iframe.src.match(/embed\/([^?&]+)/))?.[1]
      btn.addEventListener('click', e => {
        e.preventDefault()
        const target = id ? `https://www.youtube.com/watch?v=${id}` : iframe.src
        window.location.href = target
      })
    }
    // Fallback: if user taps and playback doesn't start soon, show overlay
    let tapTs = 0
    wrap.addEventListener('click', () => { tapTs = Date.now(); setTimeout(() => {
      if(!overlay) return
      // If still no activity after 1500ms since tap, show overlay
      if(Date.now() - tapTs >= 1400) overlay.classList.add('show')
    }, 1500) }, {passive:true})
  })
})
