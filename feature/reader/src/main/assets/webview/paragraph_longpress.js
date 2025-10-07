// Injected helper to detect long-press on paragraph (<p>) elements.
// It assigns each paragraph a stable data-lwb-pid attribute (index order) if missing.
// Long press (press > LONG_PRESS_MS without significant move) triggers window.LwbBridge.onParagraphLongPress(pid, text).

(function() {
  if (window.__LWB_P_LONGPRESS_INSTALLED__) return;
  window.__LWB_P_LONGPRESS_INSTALLED__ = true;

  const LONG_PRESS_MS = 450; // align with typical Android long-press threshold
  const MOVE_TOLERANCE = 12; // px squared distance tolerance

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

  function getParagraphTarget(el) {
    while (el) {
      if (el.tagName && el.tagName.toLowerCase() === 'p') return el;
      el = el.parentElement;
    }
    return null;
  }

  let pressTimer = null;
  let downX = 0, downY = 0;
  let activeP = null;

  function clearTimer() {
    if (pressTimer) {
      clearTimeout(pressTimer);
      pressTimer = null;
    }
    activeP = null;
  }

  function onDown(ev) {
    const p = getParagraphTarget(ev.target);
    if (!p) return;
    ensureIds();
    activeP = p;
    downX = ev.clientX; downY = ev.clientY;
    pressTimer = setTimeout(() => {
      if (!activeP) return;
      try {
        const id = activeP.getAttribute('data-lwb-pid') || '';
        const text = (activeP.innerText || '').trim();
        if (id && text && window.LwbBridge && window.LwbBridge.onParagraphLongPress) {
          window.LwbBridge.onParagraphLongPress(id, text);
        }
      } catch (_) {}
      clearTimer();
    }, LONG_PRESS_MS);
  }

  function onMove(ev) {
    if (!pressTimer) return;
    const dx = ev.clientX - downX;
    const dy = ev.clientY - downY;
    if ((dx*dx + dy*dy) > (MOVE_TOLERANCE * MOVE_TOLERANCE)) {
      clearTimer();
    }
  }

  function onUpCancel() { clearTimer(); }

  document.addEventListener('pointerdown', onDown, true);
  document.addEventListener('pointermove', onMove, true);
  document.addEventListener('pointerup', onUpCancel, true);
  document.addEventListener('pointercancel', onUpCancel, true);
})();
// Detect long press on paragraph elements and call Android bridge
// Timing values chosen to avoid conflict with tap detection in native layer.
(function() {
  if (window.__LWB_P_LONGPRESS_INSTALLED__) return;
  window.__LWB_P_LONGPRESS_INSTALLED__ = true;
  var PRESS_THRESHOLD_MS = 450; // long press duration
  var moveTolerance = 12; // px squared distance tolerance (approx)
  var downTs = 0;
  var downX = 0;
  var downY = 0;
  var timer = null;
  var activeP = null;

  function clear() {
    if (timer) { clearTimeout(timer); timer = null; }
    activeP = null;
  }

  function findParagraph(node) {
    while (node) {
      if (node.tagName === 'P') return node;
      node = node.parentElement;
    }
    return null;
  }

  document.addEventListener('touchstart', function(e) {
    if (!e.touches || e.touches.length !== 1) return;
    var t = e.touches[0];
    downX = t.clientX; downY = t.clientY; downTs = Date.now();
    activeP = findParagraph(e.target);
    if (!activeP) { clear(); return; }
    timer = setTimeout(function() {
      if (!activeP) return;
      try {
        var text = activeP.innerText || activeP.textContent || '';
        if (text.length > 1024) text = text.substring(0,1024);
        if (window.LwbParagraphBridge && window.LwbParagraphBridge.onParagraphLongPress) {
          window.LwbParagraphBridge.onParagraphLongPress(text);
        }
      } catch (_err) {}
      clear();
    }, PRESS_THRESHOLD_MS);
  }, { passive: true });

  document.addEventListener('touchmove', function(e) {
    if (!activeP) return;
    var t = e.touches && e.touches[0];
    if (!t) { clear(); return; }
    var dx = t.clientX - downX; var dy = t.clientY - downY;
    if ((dx*dx + dy*dy) > (moveTolerance*moveTolerance)) { clear(); }
  }, { passive: true });

  ['touchend','touchcancel','scroll'].forEach(function(ev) {
    document.addEventListener(ev, clear, { passive: true });
  });
})();