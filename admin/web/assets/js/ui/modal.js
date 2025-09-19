export function confirm(message, { confirmText = 'Confirm', cancelText = 'Cancel', danger = false } = {}){
  const overlay = document.getElementById('modal-overlay')
  const closeBtn = document.getElementById('modal-close')
  const modalContent = document.getElementById('modal-content')
  return new Promise((resolve) => {
    function hide(){
      overlay.hidden = true
      overlay.classList.remove('overlay')
      closeBtn.onclick = null
    }
    modalContent.innerHTML = `
      <div style="display:grid; gap:12px">
        <div style="font-size:16px; line-height:1.4">${message}</div>
        <div style="display:flex; gap:8px; justify-content:flex-end">
          <button class="button secondary" id="modal-cancel">${cancelText}</button>
          <button class="button ${danger ? 'danger' : ''}" id="modal-ok">${confirmText}</button>
        </div>
      </div>`
    overlay.hidden = false
    overlay.classList.add('overlay')
    const ok = document.getElementById('modal-ok')
    const cancel = document.getElementById('modal-cancel')
    const onCancel = () => { hide(); resolve(false) }
    const onOk = () => { hide(); resolve(true) }
    cancel.onclick = onCancel
    ok.onclick = onOk
    closeBtn.onclick = onCancel
    overlay.addEventListener('click', (e) => { if(e.target === overlay) onCancel() }, { once: true })
  })
}
