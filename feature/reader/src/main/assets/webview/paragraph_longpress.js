// Unified paragraph long-press detector.
// Guarantees a single callback per completed long press and prevents duplicate installations.
// Invokes window.LwbBridge.onParagraphLongPress(id, text) if available.
(function() {
  if (window.__LWB_P_LONGPRESS_INSTALLED__) return;
  window.__LWB_P_LONGPRESS_INSTALLED__ = true;

  const LONG_PRESS_MS = 500; // match Android debounce a bit higher than default
  const MOVE_TOLERANCE_SQ = 12 * 12; // squared distance tolerance
  let timer = null;
  let downX = 0;
  let downY = 0;
  let activeP = null;
  let firedForPress = false;

  function ensureIds() {
    const ps = document.querySelectorAll('p');
    let i = 0;
    ps.forEach(p => {
      if (!p.hasAttribute('data-lwb-pid')) {
        p.setAttribute('data-lwb-pid', String(i));
      }
      i++;
    });
  }

  function findParagraph(el) {
    while (el) {
      if (el.tagName && el.tagName.toLowerCase() === 'p') return el;
      el = el.parentElement;
    }
    return null;
  }

  function clear() {
    if (timer) { clearTimeout(timer); timer = null; }
    activeP = null;
    firedForPress = false;
  }

  function fireOnce() {
    if (!activeP || firedForPress) return;
    try {
      const id = activeP.getAttribute('data-lwb-pid') || '';
      const text = (activeP.innerText || activeP.textContent || '').trim();
      // highlight immediately
      if (id) {
        if (typeof window.lwbHighlightParagraph === 'function') {
          window.lwbHighlightParagraph(id);
        }
      }
      if (id && text && window.LwbBridge && typeof window.LwbBridge.onParagraphLongPress === 'function') {
        firedForPress = true;
        window.LwbBridge.onParagraphLongPress(id, text);
      }
    } catch (_) {}
  }

  function scheduleFire() {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      fireOnce();
      clear();
    }, LONG_PRESS_MS);
  }

  function onPointerDown(ev) {
    const p = findParagraph(ev.target);
    if (!p) return;
    ensureIds();
    activeP = p;
    downX = ev.clientX; downY = ev.clientY;
    firedForPress = false;
    scheduleFire();
  }
  function onPointerMove(ev) {
    if (!activeP || !timer) return;
    const dx = ev.clientX - downX;
    const dy = ev.clientY - downY;
    if ((dx * dx + dy * dy) > MOVE_TOLERANCE_SQ) {
      clear();
    }
  }
  function onPointerEnd() { clear(); }

  document.addEventListener('pointerdown', onPointerDown, true);
  document.addEventListener('pointermove', onPointerMove, true);
  document.addEventListener('pointerup', onPointerEnd, true);
  document.addEventListener('pointercancel', onPointerEnd, true);

  // Highlight support
  const HIGHLIGHT_CLASS = 'lwb-paragraph-highlight';
  const STYLE_ID = 'lwb-paragraph-highlight-style';
  function ensureHighlightStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const st = document.createElement('style');
    st.id = STYLE_ID;
    // Use !important and add outline as fallback in case page CSS overrides background.
    st.textContent = `.${HIGHLIGHT_CLASS}{background:rgba(255,215,0,0.34)!important;outline:2px solid rgba(255,215,0,0.8)!important;transition:background .25s ease, outline-color .25s ease}`;
    document.head.appendChild(st);
  }
  ensureHighlightStyle();
  let lastHighlighted = null;
  function highlightNow(pid) {
    ensureIds();
    const sel = document.querySelector('p[data-lwb-pid="'+pid+'"]');
    if (!sel) return false;
    if (lastHighlighted && lastHighlighted !== sel) {
      lastHighlighted.classList.remove(HIGHLIGHT_CLASS);
    }
    sel.classList.add(HIGHLIGHT_CLASS);
    lastHighlighted = sel;
    return true;
  }
  window.lwbHighlightParagraph = function(pid) {
    try {
      if (highlightNow(pid)) return true;
      // Retry shortly after in case DOM not ready for this paragraph yet.
      setTimeout(() => highlightNow(pid), 50);
      setTimeout(() => highlightNow(pid), 150);
      return false;
    } catch(_) { return false; }
  };
  // Reapply highlight on DOMContentLoaded/load if lastHighlighted lost styling due to reflow.
  function reapplyLast() {
    if (lastHighlighted && !lastHighlighted.classList.contains(HIGHLIGHT_CLASS)) {
      lastHighlighted.classList.add(HIGHLIGHT_CLASS);
    }
  }
  window.addEventListener('DOMContentLoaded', reapplyLast, false);
  window.addEventListener('load', reapplyLast, false);
  // Debug helper
  window.__lwbDebugHighlightInfo = function() { return { lastId: lastHighlighted?.getAttribute('data-lwb-pid') || null }; };
  // Clear highlight externally
  window.lwbClearParagraphHighlight = function() {
    try {
      if (lastHighlighted) {
        lastHighlighted.classList.remove(HIGHLIGHT_CLASS);
      }
      lastHighlighted = null;
      return true;
    } catch(_) { return false; }
  };
})();