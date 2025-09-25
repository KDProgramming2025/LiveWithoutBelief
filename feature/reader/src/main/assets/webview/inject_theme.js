(function(){
  window.lwbApplyThemeCss = function(cssBase64){
    try {
      var css = atob(cssBase64);
      var s = document.getElementById('lwb-reader-theme');
      if (!s) {
        s = document.createElement('style');
        s.id = 'lwb-reader-theme';
        (document.head || document.documentElement).appendChild(s);
      }
      if (s.innerHTML !== css) { s.innerHTML = css; }
    } catch (e) { /* no-op */ }
  }
  window.lwbApplyReaderVars = function(fontScale, lineHeight, bg){
    try {
      var r = document.documentElement;
      if (typeof fontScale === 'number' && !isNaN(fontScale) && fontScale > 0) {
        // Base 16px scaled by fontScale
        r.style.setProperty('--lwb-font-size', (16 * fontScale) + 'px');
      }
      if (typeof lineHeight === 'number' && !isNaN(lineHeight) && lineHeight > 0) {
        r.style.setProperty('--lwb-line-height', String(lineHeight));
      }
      if (typeof bg === 'string' && bg) {
        r.style.setProperty('--lwb-bg', bg);
        // Also assign inline background to html/body with !important to defeat aggressive site CSS
        try { document.documentElement.style.setProperty('background', bg, 'important'); } catch(e){}
        try { document.documentElement.style.setProperty('background-color', bg, 'important'); } catch(e){}
        try { if (document.body) { document.body.style.setProperty('background', bg, 'important'); document.body.style.setProperty('background-color', bg, 'important'); } } catch(e){}
      }
    } catch(e) { /* no-op */ }
  }
})();


