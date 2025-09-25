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
          'body *:not(img):not(video):not(canvas):not(svg):not(code):not(pre):not(figure){'+
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
})();
