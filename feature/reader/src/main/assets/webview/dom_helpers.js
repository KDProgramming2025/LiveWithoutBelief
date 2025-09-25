(function(){
  function ctx(){ var d=document; return { d: d, h: d.head || d.documentElement }; }
  window.lwbEnsureThemeLink = function(){
    try{
      var c = ctx(), d=c.d, h=c.h;
      var link = d.getElementById('lwb-theme-link');
      var href = window.location.origin + '/lwb-theme.css?v=' + Date.now();
      if(!link){ link = d.createElement('link'); link.id='lwb-theme-link'; link.rel='stylesheet'; link.href=href; h.appendChild(link);} else { link.href = href; }
    }catch(e){}
  }
  window.lwbRefreshThemeLink = function(){
    try{
      var d=document; var link=d.getElementById('lwb-theme-link');
      if(link){ link.href = window.location.origin + '/lwb-theme.css?v=' + Date.now(); }
    }catch(e){}
  }
  window.lwbEnsureBgOverride = function(){
    try{
      var c = ctx(), d=c.d, h=c.h;
      var s = d.getElementById('lwb-bg-override');
      if(!s){
        s = d.createElement('style'); s.id='lwb-bg-override';
        s.textContent = ''+
          'html,body,main,#root,#__next,#app,#content,.content,.container,[role="main"]{'+
          '  background:var(--lwb-bg) !important;'+
          '  background-color:var(--lwb-bg) !important;'+
          '}\n'+
          // Make all nested element backgrounds transparent so our solid page bg shows through
          // Exclude YouTube deep-link button and overlay card to preserve brand red and overlay styles
          'body *:not(img):not(video):not(canvas):not(svg):not(code):not(pre):not(figure):not(.yt-open-btn):not(.yt-card){'+
          '  background:transparent !important;'+
          '  background-color:transparent !important;'+
          '}';
        h.appendChild(s);
      }
    }catch(e){}
  }
  window.lwbDisableColorSchemeDarkening = function(){
    try{
      var d=document;
      var metas = d.querySelectorAll('meta[name="color-scheme"]');
      metas.forEach(function(m){ m.parentNode && m.parentNode.removeChild(m); });
      var s = d.getElementById('lwb-color-scheme-override');
      if(!s){
        s = d.createElement('style'); s.id='lwb-color-scheme-override';
        s.textContent = 'html{color-scheme: light !important;}';
        (d.head||d.documentElement).appendChild(s);
      }
    }catch(e){}

  }
  window.lwbEnsureLightMeta = function(){
    try{
      var d=document, h=d.head||d.documentElement;
      var existing = d.querySelector('meta[name="color-scheme"]');
      if (!existing){
        var meta = d.createElement('meta');
        meta.name = 'color-scheme';
        meta.content = 'light';
        h.appendChild(meta);
      } else if (existing.content !== 'light') {
        existing.content = 'light';
      }
    }catch(e){}
  }
  // ----- Anchor helpers for precise scroll restore -----
  function lwbElemTop(el){ try{ return el.getBoundingClientRect().top + window.scrollY; }catch(e){ return 0; } }
  function lwbCssPath(el){
    try{
      if (!el || !el.tagName) return null;
      if (el.id) return '#' + (window.CSS && CSS.escape ? CSS.escape(el.id) : el.id.replace(/([ #;?%&,.+*~\':"!^$\[\]\(\)=>|\/])/g,'\\$1'));
      var path = [];
      var cur = el; var depth = 0;
      while (cur && cur.nodeType === 1 && depth < 8 && cur !== document.body && cur !== document.documentElement){
        var name = cur.tagName.toLowerCase();
        if (cur.id){ path.unshift(name + '#' + (window.CSS && CSS.escape ? CSS.escape(cur.id) : cur.id)); break; }
        var sibs = 0, idx = 0; var n = cur;
        while (n){ if (n.nodeType===1 && n.tagName===cur.tagName){ sibs++; if (n===cur) idx=sibs; } n = n.previousElementSibling; }
        var part = name + (sibs>1 ? ':nth-of-type(' + idx + ')' : '');
        path.unshift(part);
        cur = cur.parentElement; depth++;
      }
      return path.length ? path.join('>') : null;
    }catch(e){ return null; }
  }
  window.lwbGetViewportAnchor = function(){
    try{
      var y = window.scrollY || 0;
      var candidates = Array.from(document.querySelectorAll('[id], h1, h2, h3, h4, h5, h6, figure, blockquote, p, section, article'));
      if (!candidates.length) return '';
      var best = null; var bestTop = -1;
      for (var i=0;i<candidates.length;i++){
        var el = candidates[i]; var top = lwbElemTop(el);
        if (top <= y + 8 && top >= bestTop){ bestTop = top; best = el; }
      }
      if (!best){
        best = candidates[0]; bestTop = lwbElemTop(best);
        for (var j=1;j<candidates.length;j++){ var t=lwbElemTop(candidates[j]); if (t<bestTop) { bestTop=t; best=candidates[j]; } }
      }
      var by = best.id ? 'id' : 'css';
      var sel = best.id ? best.id : lwbCssPath(best);
      if (!sel) return '';
      var off = Math.max(0, y - bestTop);
      return JSON.stringify({ by: by, sel: sel, off: off });
    }catch(e){ return ''; }
  }
  window.lwbScrollToAnchor = function(anchorJson){
    try{
      if (!anchorJson) return false;
      var a = typeof anchorJson === 'string' ? JSON.parse(anchorJson) : anchorJson;
      var el = a.by === 'id' ? document.getElementById(a.sel) : document.querySelector(a.sel);
      if (!el) return false;
      var top = lwbElemTop(el) + (a.off||0);
      window.scrollTo(0, Math.max(0, top|0));
      return true;
    }catch(e){ return false; }
  }

  // ----- Extract main article text for offline TTS -----
  window.lwbGetArticleText = function(){
    try{
      var root = document.querySelector('.article, main, article, #content, [role="main"]') || document.body;
      if (!root) return '';
      var clone = root.cloneNode(true);
      // Remove non-content nodes
      var selectors = ['script','style','nav','footer','aside','form','button','input','textarea','select','iframe'];
      selectors.forEach(function(sel){ Array.from(clone.querySelectorAll(sel)).forEach(function(n){ n.remove(); }); });
      // Convert <br> to newline for better flow
      Array.from(clone.querySelectorAll('br')).forEach(function(br){ br.replaceWith('\n'); });
      var text = (clone.innerText || clone.textContent || '').replace(/\s+\n/g,'\n').replace(/\n{3,}/g,'\n\n');
      return text.trim();
    }catch(e){ return ''; }
  }
})();
