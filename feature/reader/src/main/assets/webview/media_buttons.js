// Attach a delegated click handler for media question buttons.
// Calls window.LwbBridge.onMediaQuestion(kind, src) if available.
(function () {
  if (window.__LWB_MEDIA_BTN_INSTALLED__) return;
  window.__LWB_MEDIA_BTN_INSTALLED__ = true;

  function findBtn(el) {
    while (el) {
      if (el.nodeType === 1 && el.hasAttribute && el.hasAttribute('data-lwb-media-question')) return el;
      el = el.parentElement;
    }
    return null;
  }

  document.addEventListener(
    'click',
    function (ev) {
      try {
        var btn = findBtn(ev.target);
        if (!btn) return;
        ev.preventDefault();
        ev.stopPropagation();
        var kind = (btn.getAttribute('data-kind') || '').trim();
        var src = (btn.getAttribute('data-src') || '').trim();
        if (window.LwbBridge && typeof window.LwbBridge.onMediaQuestion === 'function') {
          window.LwbBridge.onMediaQuestion(kind, src);
        }
      } catch (_) {
        // swallow
      }
    },
    true
  );
})();
