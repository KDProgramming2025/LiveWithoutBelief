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
        var fs = (16 * fontScale) + 'px';
        r.style.setProperty('--lwb-font-size', fs);
        try { document.documentElement.style.setProperty('font-size', fs, 'important'); } catch(e){}
        try { if (document.body) document.body.style.setProperty('font-size', fs, 'important'); } catch(e){}
      }
      if (typeof lineHeight === 'number' && !isNaN(lineHeight) && lineHeight > 0) {
        var lh = String(lineHeight);
        r.style.setProperty('--lwb-line-height', lh);
        try { document.documentElement.style.setProperty('line-height', lh, 'important'); } catch(e){}
        try { if (document.body) document.body.style.setProperty('line-height', lh, 'important'); } catch(e){}
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


