(function () {
  try {
    // Ensure viewport is set for proper scaling
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'viewport';
      meta.content = 'width=device-width, initial-scale=1, maximum-scale=1';
      document.head.appendChild(meta);
    }

    // Base CSS to prevent horizontal scroll and constrain media
    // Link external CSS from assets via the same base URL
    var link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'lwb-assets://webview/inject_clamp.css';
    document.head.appendChild(link);

    // Wrap YouTube iframes in a responsive container if not already
    // No invasive YouTube manipulation to avoid breaking content.

    // Enforce nodownload on HTML5 media controls and disable context menus
    function enforceNoDownload() {
      var media = document.querySelectorAll('audio, video');
      media.forEach(function (el) {
        try {
          var list = (el.getAttribute('controlsList') || '').toLowerCase();
          var tokens = list.split(/\s+/).filter(Boolean);
          if (!tokens.includes('nodownload')) tokens.push('nodownload');
          if (!tokens.includes('noplaybackrate')) tokens.push('noplaybackrate');
          el.setAttribute('controlsList', tokens.join(' '));
          el.setAttribute('oncontextmenu', 'return false');
          el.addEventListener('contextmenu', function (e) { e.preventDefault(); }, { passive: false });
        } catch (e) { /* no-op */ }
      });
    }
    enforceNoDownload();
    var mo = new MutationObserver(function () { enforceNoDownload(); });
    mo.observe(document.documentElement || document.body, { childList: true, subtree: true, attributes: true });
    document.addEventListener('contextmenu', function (e) {
      var t = e.target;
      if (t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO')) { e.preventDefault(); }
    }, { passive: false });
  } catch (e) {
    // no-op
  }
})();
