package com.example.flow.web

/**
 * Round 14 — the JavaScript that runs INSIDE the Flow page.
 *
 * Three blocks:
 *  - [SETUP]   : viewport fix (pinch-zoom/scroll), composer + button finders, native value setter
 *  - [CAPTURE] : arms a baseline per prompt, watches DOM/canvas, reports ONLY new large media
 *                (never the account avatar / logos / header art) and turns blob:/canvas results into
 *                base64 chunks the app can save
 *  - [DRIVER]  : arm / submit / state / zoom / import-page
 *
 * Written in ES5 so it also works on old WebView builds (minSdk 26).
 */
object FlowJs {

    val SETUP = """
(function(){
  var S = window.__saifFlow = window.__saifFlow || {};
  if (S.setupDone) return 'ok';
  S.setupDone = true;
  S.seen = {};
  S.base = {};
  S.promptIndex = -1;
  S.zoomLevel = 1;

  S.fixViewport = function(){
    try {
      var head = document.head || document.documentElement;
      var m = document.querySelector('meta[name="viewport"]');
      if (!m) { m = document.createElement('meta'); m.setAttribute('name','viewport'); head.appendChild(m); }
      m.setAttribute('content','width=device-width, initial-scale=1, minimum-scale=0.25, maximum-scale=6, user-scalable=yes');
      var st = document.documentElement.style;
      st.touchAction = 'pan-x pan-y pinch-zoom';
      st.webkitTextSizeAdjust = '100%';
      if (document.body) document.body.style.webkitTextSizeAdjust = '100%';
    } catch(e) {}
  };

  S.zoom = function(z){
    S.zoomLevel = Math.min(3, Math.max(0.5, z));
    try { document.documentElement.style.zoom = String(S.zoomLevel); } catch(e) {}
    return S.zoomLevel;
  };

  S.composer = function(){
    var list = Array.prototype.slice.call(document.querySelectorAll('textarea, [contenteditable="true"], [role="textbox"]'));
    var ok = list.filter(function(e){
      var r = e.getBoundingClientRect();
      if (!r || r.width < 80 || r.height < 14) return false;
      if (e.disabled || e.readOnly) return false;
      return true;
    });
    ok.sort(function(a,b){
      var ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect();
      return (rb.width * rb.height) - (ra.width * ra.height);
    });
    return ok.length ? ok[0] : null;
  };

  function setValue(el, value){
    try { el.focus(); } catch(e) {}
    var tag = el.tagName;
    if (tag === 'TEXTAREA' || tag === 'INPUT') {
      var proto = tag === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
      var desc = Object.getOwnPropertyDescriptor(proto, 'value');
      if (desc && desc.set) { desc.set.call(el, value); } else { el.value = value; }
    } else {
      el.innerText = value;
      try {
        var range = document.createRange();
        range.selectNodeContents(el);
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        document.execCommand('insertText', false, value);
      } catch(e) {}
    }
    try {
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    } catch(e) {}
  }

  S.genButton = function(){
    var nodes = Array.prototype.slice.call(document.querySelectorAll('button,[role="button"],[data-testid]'));
    var best = null, bestScore = 0;
    for (var i = 0; i < nodes.length; i++) {
      var b = nodes[i];
      if (b.disabled) continue;
      var r = b.getBoundingClientRect();
      if (!r || r.width < 8 || r.height < 8) continue;
      var t = ((b.innerText || '') + ' ' + (b.getAttribute('aria-label') || '') + ' ' +
               (b.getAttribute('title') || '') + ' ' + (b.getAttribute('data-testid') || '')).toLowerCase();
      var s = 0;
      if (t.indexOf('generate') >= 0) s += 6;
      if (t.indexOf('create') >= 0) s += 4;
      if (t.indexOf('make') >= 0) s += 2;
      if (t.indexOf('arrow_forward') >= 0 || t.indexOf('send') >= 0 || t.indexOf('submit') >= 0) s += 3;
      if (t.indexOf('image') >= 0 || t.indexOf('video') >= 0) s += 1;
      if (t.indexOf('cancel') >= 0 || t.indexOf('close') >= 0 || t.indexOf('delete') >= 0) s -= 8;
      if (s > bestScore) { bestScore = s; best = b; }
    }
    return best;
  };

  S.setPrompt = function(text){
    var c = S.composer();
    if (!c) return false;
    setValue(c, text);
    return true;
  };

  S.pressEnter = function(){
    var c = S.composer();
    if (!c) return false;
    try {
      c.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
      c.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
      return true;
    } catch(e) { return false; }
  };

  S.clickGenerate = function(){
    var b = S.genButton();
    if (b) { try { b.click(); return true; } catch(e) {} }
    if (S.pressEnter()) return true;
    try {
      var f = document.querySelector('form');
      if (f) { if (f.requestSubmit) { f.requestSubmit(); return true; } f.submit(); return true; }
    } catch(e) {}
    return false;
  };

  S.fixViewport();
  setTimeout(S.fixViewport, 1200);
  setTimeout(S.fixViewport, 3500);
  return 'ok';
})();
"""

    /** Media capture. Returns 'ok' the first time, 'done' afterwards. */
    val CAPTURE = """
(function(){
  var S = window.__saifFlow = window.__saifFlow || {};
  if (S.captureDone) return 'done';
  S.captureDone = true;
  S.seen = S.seen || {};
  S.base = S.base || {};
  S.armed = false;

  function abs(u){
    if (!u) return '';
    if (u.indexOf('blob:') === 0 || u.indexOf('data:') === 0) return u;
    try { return new URL(u, location.href).href; } catch(e) { return u; }
  }

  function badUrl(u){
    if (!u) return true;
    if (u.indexOf('data:') === 0) return true;
    var l = u.toLowerCase();
    if (l.indexOf('googleusercontent.com/a/') >= 0) return true;
    if (l.indexOf('googleusercontent.com/a-/') >= 0) return true;
    if (l.indexOf('googleusercontent.com/ogw') >= 0) return true;
    if (/=[swh]\d{1,3}(-c)?(&|$)/.test(l)) return true;
    if (/\.svg(\?|$)/.test(l)) return true;
    if (/(avatar|profile|logo|favicon|sprite|icon|placeholder|emoji|badge)/.test(l)) return true;
    if (l.indexOf('gstatic.com') >= 0) return true;
    if (l.indexOf('/accounts/') >= 0) return true;
    return false;
  }

  function inChrome(el){
    try {
      return !!(el.closest && el.closest('header,nav,[role="banner"],[role="navigation"],[aria-label*="account" i],[aria-label*="profile" i],[aria-label*="avatar" i]'));
    } catch(e) { return false; }
  }

  function sendB64(id, mime, name, w, h, dataUrl, pIdx){
    try {
      var comma = dataUrl.indexOf(',');
      if (comma < 0) return;
      var raw = dataUrl.slice(comma + 1);
      var size = 200000;
      var total = Math.ceil(raw.length / size);
      SaifFlow.onBase64Start(id, mime, w, h, pIdx, String(name).slice(0, 60), total);
      for (var i = 0; i < total; i++) {
        SaifFlow.onBase64Chunk(id, i, raw.substr(i * size, size));
      }
      SaifFlow.onBase64End(id);
    } catch(e) {}
  }

  S.resolveBlob = function(url, token){
    function done(dataUrl, mime, w, h){
      try { sendB64(String(token), mime || 'image/png', 'grabbed_media', w || 0, h || 0, String(dataUrl || ''), -1); } catch(e) {}
    }
    function viaXhr(){
      try {
        var x = new XMLHttpRequest();
        x.open('GET', url, true);
        x.responseType = 'blob';
        x.onload = function(){
          if (x.status >= 200 && x.status < 300 && x.response) {
            var fr = new FileReader();
            fr.onload = function(){ done(fr.result, x.response.type); };
            fr.readAsDataURL(x.response);
          } else { try { SaifFlow.onLog('grab failed http ' + x.status); } catch(e) {} }
        };
        x.onerror = function(){ try { SaifFlow.onLog('grab network error'); } catch(e) {} };
        x.send();
      } catch(e) { try { SaifFlow.onLog('grab exception'); } catch(e2) {} }
    }
    try {
      fetch(url, { credentials: 'include' }).then(function(r){
        if (!r.ok) throw new Error('http ' + r.status);
        return r.blob();
      }).then(function(b){
        var fr = new FileReader();
        fr.onload = function(){ done(fr.result, b.type); };
        fr.readAsDataURL(b);
      }).catch(function(){ viaXhr(); });
      return 'ok';
    } catch(e) { viaXhr(); return 'ok'; }
  };

  function report(u, mime, el, w, h, pIdx){
    if (!S.armed) return false;                       // ROUND 15.4: no prompt armed = no reporting
    if (S.promptIndex !== undefined && pIdx !== S.promptIndex) return false;  // only the running prompt
    if (S.seen[u]) return false;
    S.seen[u] = 1;
    var name = (el && (el.getAttribute('aria-label') || el.getAttribute('alt') || el.getAttribute('title'))) || 'flow_media';
    try { SaifFlow.onMedia(u, mime, w || 0, h || 0, pIdx, String(name).slice(0, 60)); } catch(e) {}
    return true;
  }

  // Open shadow roots hide Flow's result tiles on some builds: walk them too (depth limited).
  function collectMedia(root, out, depth){
    if (!root || depth > 3) return out;
    try {
      var list = root.querySelectorAll ? root.querySelectorAll('img,video') : [];
      for (var i = 0; i < list.length; i++) out.push(list[i]);
      var all = root.querySelectorAll ? root.querySelectorAll('*') : [];
      for (var j = 0; j < all.length; j++) {
        var el = all[j];
        if (el && el.shadowRoot) collectMedia(el.shadowRoot, out, depth + 1);
      }
    } catch(e) {}
    return out;
  }

  S.scan = function(force){
    if (!S.armed) return 0;                           // ROUND 15.4: idle page never reports a picture
    var found = 0;
    var imgs = collectMedia(document, [], 0);
    for (var i = 0; i < imgs.length; i++) {
      var e = imgs[i];
      var tag = e.tagName;
      var w = e.naturalWidth || e.videoWidth || 0;
      var h = e.naturalHeight || e.videoHeight || 0;
      var r = e.getBoundingClientRect ? e.getBoundingClientRect() : { width: w, height: h };
      if (w < 200 || h < 200) continue;
      if (r && (r.width < 140 || r.height < 140)) continue;
      if (inChrome(e)) continue;
      var u = '';
      if (tag === 'VIDEO') {
        u = e.currentSrc || e.src || '';
        if (!u) { var s = e.querySelector('source'); if (s) u = s.src || ''; }
      } else {
        u = e.currentSrc || e.src || '';
      }
      u = abs(u);
      if (!u || badUrl(u)) continue;
      if (S.base[u]) continue;
      if (report(u, tag === 'VIDEO' ? 'video' : 'image', e, w, h, S.promptIndex)) found++;
    }
    // Result tiles painted as CSS backgrounds (newer Flow builds do this instead of <img>).
    var bgEls = document.querySelectorAll('[style*="background-image"]');
    for (var b = 0; b < bgEls.length; b++) {
      var bd = bgEls[b];
      var rr3 = bd.getBoundingClientRect ? bd.getBoundingClientRect() : null;
      if (!rr3 || rr3.width < 240 || rr3.height < 240) continue;
      if (inChrome(bd)) continue;
      var bi = bd.style && bd.style.backgroundImage;
      if (!bi) { try { bi = getComputedStyle(bd).backgroundImage; } catch(e) { bi = ''; } }
      var mm = /url\(["']?(.*?)["']?\)/.exec(bi || '');
      if (!mm) continue;
      var bu = abs(mm[1]);
      if (!bu || badUrl(bu)) continue;
      if (S.base[bu] || S.seen[bu]) continue;
      if (report(bu, 'image', bd, Math.round(rr3.width), Math.round(rr3.height), S.promptIndex)) found++;
    }

    // Canvas-rendered results (some Flow variants paint the result on a canvas)
    var canvases = document.querySelectorAll('canvas');
    for (var c = 0; c < canvases.length; c++) {
      var cv = canvases[c];
      var rr = cv.getBoundingClientRect();
      if (!rr || rr.width < 240 || rr.height < 240) continue;
      if (inChrome(cv)) continue;
      var key = 'canvas:' + cv.width + 'x' + cv.height + ':' + Math.round(rr.top);
      if (S.base[key] || S.seen[key]) continue;
      S.seen[key] = 1;
      try {
        var d = cv.toDataURL('image/png');
        if (d && d.length > 30000) { sendB64(key, 'image/png', 'canvas_media', cv.width, cv.height, d, S.promptIndex); found++; }
      } catch(e) {}
    }
    return found;
  };

  S.snapshot = function(){
    var snap = {};
    var all = document.querySelectorAll('img,video');
    for (var i = 0; i < all.length; i++) {
      var e = all[i];
      var u = abs(e.currentSrc || e.src || '');
      if (u) snap[u] = 1;
    }
    // ROUND 15.4: older projects paint results as CSS backgrounds — they must be in the "already there"
    // snapshot too, otherwise a brand-new prompt can wrongly "capture" an old picture.
    var bgs = document.querySelectorAll('[style*="background-image"]');
    for (var b = 0; b < bgs.length; b++) {
      var bi = bgs[b].style && bgs[b].style.backgroundImage;
      var mm = /url\(["']?(.*?)["']?\)/.exec(bi || '');
      if (mm && mm[1]) snap[abs(mm[1])] = 1;
    }
    var cvs = document.querySelectorAll('canvas');
    for (var c = 0; c < cvs.length; c++) {
      try {
        var rr = cvs[c].getBoundingClientRect();
        snap['canvas:' + cvs[c].width + 'x' + cvs[c].height + ':' + Math.round(rr.top)] = 1;
      } catch(e) {}
    }
    return snap;
  };

  S.arm = function(index){
    S.promptIndex = index;
    S.armed = true;
    S.seen = {};                                     // ROUND 15.4: fresh start for every prompt
    var now = S.snapshot();
    for (var k in now) { if (now.hasOwnProperty(k)) S.base[k] = 1; }
    S.scan(true);
    return S.promptIndex;
  };

  S.unarm = function(){ S.armed = false; S.promptIndex = -1; return 'ok'; };

  S.scanCount = function(){ return Object.keys(S.seen).length; };

  try {
    var mo = new MutationObserver(function(){ S.scan(false); });
    mo.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['src','srcset'] });
  } catch(e) {}
  setInterval(function(){ S.scan(false); }, 1600);
  setTimeout(function(){ S.scan(true); }, 800);
  return 'ok';
})();
"""

    /** Page description used by the autopilot + login banner. */
    val STATE = """
(function(){
  var S = window.__saifFlow || {};
  var c = S.composer ? S.composer() : null;
  var body = '';
  try { body = (document.body && document.body.innerText) ? document.body.innerText.toLowerCase() : ''; } catch(e) {}
  var accountPage = location.hostname.indexOf('accounts.google.com') >= 0;
  var signInText = /(sign in|choose an account|use your google account|continue with google|log in)/.test(body);
  var login = accountPage || (!c && signInText);
  var count = 0;
  try { count = document.images ? document.images.length : 0; } catch(e) {}
  return {
    url: location.href,
    hasComposer: !!c,
    needsLogin: !!login,
    images: count,
    title: (document.title || '').slice(0, 80)
  };
})();
"""

    val SUBMIT = """
(function(text){
  var S = window.__saifFlow;
  if (!S || !S.setPrompt) return 'nosetup';
  if (!S.setPrompt(text)) return 'nocomposer';
  setTimeout(function(){ try { S.clickGenerate(); } catch(e) {} }, 550);
  return 'ok';
})(%s);
"""

    val ARM = """
(function(i){
  var S = window.__saifFlow;
  if (!S || !S.arm) return 'nosetup';
  return 'ok:' + S.arm(i);
})(%d);
"""

    val IMPORT_PAGE = """
(function(){
  var S = window.__saifFlow;
  if (!S || !S.scan) return 0;
  S.base = {};
  return S.scan(true);
})();
"""

    val SET_ZOOM = """
(function(z){
  var S = window.__saifFlow;
  if (!S || !S.zoom) return z;
  return S.zoom(z);
})(%s);
"""

    val PAGE_INFO = STATE

    /**
     * Round 15 — Flow often lands on the dashboard without a prompt box; a project has to be opened first.
     * This clicks the most likely "new project / start / create" control when no composer exists, so the
     * autopilot is not stuck waiting on a page that can never accept a prompt.
     */
    val NEW_PROJECT = """
(function(){
  var S = window.__saifFlow || {};
  var c = S.composer ? S.composer() : null;
  if (c) return 'already';
  var rx = /(new project|create project|start a project|start creating|create video|new video|generate video|start|create|shuru)/i;
  var els = document.querySelectorAll('button,a,[role="button"]');
  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    if (!el || el.disabled) continue;
    var t = (el.innerText || el.textContent || el.getAttribute('aria-label') || '').trim();
    if (!t || t.length > 40) continue;
    if (!rx.test(t)) continue;
    try { el.click(); return 'clicked:' + t.slice(0, 24); } catch(e) {}
  }
  return 'notfound';
})();
"""

    /**
     * ROUND 15.1 -- THE ARROW BUTTON, FOR REAL.
     *
     * Round 14/15 typed the prompt but only "asked" for a click: Flow's send control is an icon-only SVG
     * arrow with no text, so the old scorer found nothing and the page sat there until the owner pressed
     * the arrow by hand. This version scores every control by label AND by GEOMETRY (a square-ish button
     * hugging the composer's bottom-right corner), then fires a full pointer/mouse/click sequence on the
     * winner, with Enter and form-submit as the declared fallbacks.
     */
    val SUBMIT_V2 = """
(function(text){
  var S = window.__saifFlow;
  if (!S || !S.setPrompt) return 'nosetup';
  if (!S.composer) return 'nosetup';
  function label(el){
    return ((el.innerText || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' +
            (el.getAttribute('title') || '') + ' ' + (el.getAttribute('data-testid') || '') + ' ' +
            (el.getAttribute('aria-description') || '')).toLowerCase();
  }
  function nearComposer(el){
    var c = S.composer();
    if (!c) return false;
    var r = el.getBoundingClientRect(), cr = c.getBoundingClientRect();
    if (!r || !cr) return false;
    return (r.top < cr.bottom + 170 && r.bottom > cr.top - 170 && r.left > cr.left + cr.width * 0.35);
  }
  function candidates(){
    var nodes = Array.prototype.slice.call(document.querySelectorAll('button,[role="button"],div[role="button"],span[role="button"],a[role="button"]'));
    var out = [];
    var c = S.composer();
    var cr = c ? c.getBoundingClientRect() : null;
    for (var i = 0; i < nodes.length; i++) {
      var b = nodes[i];
      if (!b || b.disabled || b.getAttribute('aria-disabled') === 'true') continue;
      var r = b.getBoundingClientRect();
      if (!r || r.width < 12 || r.height < 12 || r.width > 280 || r.height > 180) continue;
      var t = label(b);
      if (t.indexOf('cancel') >= 0 || t.indexOf('close') >= 0 || t.indexOf('delete') >= 0 || t.indexOf('remove') >= 0) continue;
      if (t.indexOf('upload') >= 0 || t.indexOf('attach') >= 0 || t.indexOf('setting') >= 0) continue;
      var score = 0;
      if (t.indexOf('send') >= 0) score += 12;
      if (t.indexOf('submit') >= 0) score += 11;
      if (t.indexOf('generate') >= 0) score += 10;
      if (t.indexOf('create') >= 0) score += 7;
      if (t.indexOf('arrow') >= 0 || t.indexOf('forward') >= 0) score += 8;
      if (t.indexOf('start') >= 0 || t.indexOf('run') >= 0) score += 3;
      if (b.querySelector('svg,img,mat-icon,i')) score += 2;
      if (nearComposer(b)) score += 7;
      if (cr) {
        var dx = Math.abs((r.left + r.width / 2) - (cr.right - 26));
        var dy = Math.abs((r.top + r.height / 2) - (cr.bottom - 22));
        if (dx < 100 && dy < 110) score += 10;
      }
      if (score <= 2) continue;
      out.push({ el: b, score: score, name: (t || '').replace(/ +/g, ' ').trim().slice(0, 22) });
    }
    out.sort(function(a, b){ return b.score - a.score; });
    return out;
  }
  function fire(el){
    var r = el.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    var MP = window.PointerEvent || window.MouseEvent;
    var kinds = [['pointerover', MP], ['mouseover', MP], ['pointerdown', MP], ['mousedown', MP],
                 ['pointerup', MP], ['mouseup', MP], ['click', MP]];
    for (var i = 0; i < kinds.length; i++) {
      try {
        var ev = new kinds[i][1](kinds[i][0], { bubbles: true, cancelable: true, clientX: x, clientY: y,
          view: window, pointerId: 1, isPrimary: true, button: 0, buttons: kinds[i][0].indexOf('down') >= 0 ? 1 : 0 });
        el.dispatchEvent(ev);
      } catch(err) {}
    }
    try { el.click(); } catch(err) {}
    return true;
  }
  var typed = false;
  try { typed = !!S.setPrompt(String(text)); } catch(e) { typed = false; }
  if (!typed) return 'nocomposer';
  var tries = 0;
  var hit = null;
  while (tries < 4 && !hit) {
    var list = candidates();
    if (!list.length) break;
    fire(list[0].el);
    hit = list[0];
    tries++;
    break;
  }
  if (hit) return 'ok:click:' + (hit.name || 'button') + ':' + hit.score;
  if (S.pressEnter && S.pressEnter()) return 'ok:enter';
  try {
    var f = document.querySelector('form');
    if (f) { if (f.requestSubmit) { f.requestSubmit(); return 'ok:form'; } f.submit(); return 'ok:form'; }
  } catch(e) {}
  return 'fail:nobutton';
})(%s);
"""

    /** What the page looks like right after a submit: was the text taken, is it busy, did an image appear? */
    val GEN_STATE = """
(function(){
  var S = window.__saifFlow || {};
  function composerValue(){
    var c = S.composer ? S.composer() : null;
    if (!c) return '';
    if (c.tagName === 'TEXTAREA' || c.tagName === 'INPUT') return c.value || '';
    return c.innerText || '';
  }
  var v = composerValue();
  var body = '';
  try { body = (document.body && document.body.innerText) ? document.body.innerText.toLowerCase() : ''; } catch(e) {}
  var imgs = 0;
  try { imgs = document.images ? document.images.length : 0; } catch(e) {}
  return {
    value: v.slice(0, 160),
    busy: /(generating|creating|queued|in progress|please wait|rendering|ban raha)/.test(body),
    images: imgs,
    hasComposer: !!(S.composer && S.composer())
  };
})();
"""

    /**
     * ROUND 15.3 -- WHERE IS THE ARROW? (pure geometry scan; returns coordinates, clicks nothing).
     *
     * The build-15.2 screenshot proved the JS-side synthetic click does NOT reach Flow's send control
     * (log kept saying "arrow confirm nahi hua"). So we stop trusting synthetic DOM events: this scan
     * only reports the viewport coordinates of the square-ish icon button hugging the composer's
     * bottom-right corner (plus the raw composer corner as fallback). Kotlin then fires REAL
     * MotionEvents there -- the same thing the owner's finger does, which Flow cannot tell apart.
     */
    val FIND_SEND = """
(function(){
  var S = window.__saifFlow || {};
  if (!S || !S.composer) return { ok: false, reason: 'nosetup' };
  var c = S.composer();
  if (!c) return { ok: false, reason: 'nocomposer' };
  var cr = c.getBoundingClientRect();
  function label(el){
    if (!el || !el.getAttribute) return '';
    return ((el.innerText || el.textContent || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' +
            (el.getAttribute('title') || '') + ' ' + (el.getAttribute('data-testid') || '') + ' ' +
            (el.getAttribute('aria-description') || '')).toLowerCase().replace(/\s+/g, ' ').trim();
  }
  function box(el){
    try { return el.getBoundingClientRect(); } catch(e) { return null; }
  }
  function usable(el, r){
    if (!el || !r) return false;
    if (el.disabled) return false;
    if (el.getAttribute && el.getAttribute('aria-disabled') === 'true') return false;
    if (r.width < 14 || r.height < 14) return false;
    if (r.width > 320 || r.height > 200) return false;
    if (r.bottom < 0) return false;
    return true;
  }
  var nodes = document.querySelectorAll('button,[role="button"],div[role="button"],span[role="button"],a[role="button"],svg');
  var best = null, bestScore = 0, list = [];
  for (var i = 0; i < nodes.length; i++) {
    var raw = nodes[i];
    var el = raw;
    if (raw.closest) {
      var up = raw.closest('button,[role="button"],a[role="button"]');
      if (up) el = up;
    }
    var r = box(el);
    if (!usable(el, r)) continue;
    var t = label(el);
    if (/(cancel|close|delete|remove|upload|attach|setting|mic|record|download|share|copy|undo|redo)/.test(t)) continue;
    var score = 0;
    if (/(send|submit|generate|arrow|create|start|run|go\b)/.test(t)) score += 14;
    var dx = Math.abs((r.left + r.width / 2) - (cr.right - 30));
    var dy = Math.abs((r.top + r.height / 2) - (cr.bottom - 26));
    if (dx < 120 && dy < 130) score += 14;
    if (r.left > cr.left + cr.width * 0.5) score += 4;
    if (el.querySelector && el.querySelector('svg')) score += 3;
    if (raw.tagName && String(raw.tagName).toLowerCase() === 'svg') score += 2;
    if (Math.abs(r.width - r.height) < Math.max(8, r.width * 0.3)) score += 4;
    if (!t && el.querySelector && el.querySelector('svg,img')) score += 2;
    if (score <= 3) continue;
    var item = { x: r.left + r.width / 2, y: r.top + r.height / 2, name: (t || 'btn').slice(0, 22), score: score };
    list.push(item);
    if (score > bestScore) { bestScore = score; best = item; }
  }
  list.sort(function(a, b){ return b.score - a.score; });
  var out = {
    ok: !!best,
    x: best ? best.x : (cr.right - 30),
    y: best ? best.y : (cr.bottom - 26),
    name: best ? best.name : 'fallback-corner',
    score: bestScore,
    alt: list.slice(0, 4),
    corner: { x: cr.right - 30, y: cr.bottom - 26 },
    composer: { x: cr.left, y: cr.top, w: cr.width, h: cr.height }
  };
  return out;
})();
"""

    /** ROUND 15.3 -- type the prompt only (no click, no Enter). Returns ok / nocomposer / nosetup. */
    val TYPE_PROMPT = """
(function(text){
  var S = window.__saifFlow || {};
  if (!S || !S.setPrompt) return 'nosetup';
  var c = S.composer ? S.composer() : null;
  if (!c) return 'nocomposer';
  var ok = false;
  try { ok = !!S.setPrompt(String(text)); } catch(e) { ok = false; }
  return ok ? 'ok' : 'nocomposer';
})(%s);
"""

    /** ROUND 15.3 -- JS-only click of the best-scored send control (secondary fallback only). */
    val CLICK_SEND = """
(function(){
  var S = window.__saifFlow;
  if (!S || !S.clickGenerate) return 'nosetup';
  try { return S.clickGenerate() ? 'ok:gen' : 'fail'; } catch(e) { return 'fail'; }
})();
"""

    /** ROUND 15.3 -- pick Flow's own aspect-ratio control (9:16 / 16:9 / 1:1 ...). */
    val SET_ASPECT = """
(function(aspect){
  var want = String(aspect || '9:16').replace(/\s/g, '');
  var map = {
    '9:16': ['9:16', '9 / 16', 'portrait', 'vertical'],
    '16:9': ['16:9', '16 / 9', 'landscape', 'widescreen'],
    '1:1': ['1:1', 'square'],
    '4:5': ['4:5', '4 / 5'],
    '3:4': ['3:4', '3 / 4']
  };
  var keys = map[want] || [want];
  function norm(t){ return String(t || '').toLowerCase().replace(/\s/g, '').replace(/\//g, ''); }
  var els = document.querySelectorAll('button,[role="button"],[role="radio"],[aria-label],[data-value]');
  for (var k = 0; k < keys.length; k++) {
    var key = norm(keys[k]);
    if (!key) continue;
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (!el || el.disabled) continue;
      var r = el.getBoundingClientRect();
      if (!r || r.width < 8 || r.height < 8 || r.width > 420) continue;
      var t = norm((el.innerText || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' +
                   (el.getAttribute('title') || '') + ' ' + (el.getAttribute('data-value') || ''));
      if (t.indexOf(key) < 0) continue;
      var pressed = (el.getAttribute('aria-pressed') === 'true') || (el.getAttribute('aria-checked') === 'true');
      if (pressed) return 'already:' + keys[k];
      try { el.click(); return 'ok:' + keys[k]; } catch(e) {}
    }
  }
  return 'notfound';
})(%s);
"""

    /** ROUND 15.3 -- open the model / images-per-run chip that sits next to the composer. */
    val OPEN_COUNT_MENU = """
(function(){
  var S = window.__saifFlow || {};
  var c = S.composer ? S.composer() : null;
  if (!c) return 'nocomposer';
  var cr = c.getBoundingClientRect();
  function txt(el){
    return ((el.innerText || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' +
            (el.getAttribute('title') || '') + ' ' + (el.getAttribute('data-testid') || ''))
            .toLowerCase().replace(/\s+/g, ' ').trim();
  }
  var els = document.querySelectorAll('button,[role="button"],[role="combobox"],[aria-haspopup],div[role="button"]');
  var best = null, bestScore = 0;
  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    if (!el || el.disabled) continue;
    var r = el.getBoundingClientRect();
    if (!r || r.width < 30 || r.height < 14 || r.width > 340) continue;
    var t = txt(el);
    if (!t) continue;
    var score = 0;
    if (/(x\s?2|x\s?3|x\s?4|x2|x3|x4)/.test(t)) score += 12;
    if (/(nano|banana|imagen|veo|model|imagine)/.test(t)) score += 9;
    if (r.top > cr.top - 110 && r.bottom < cr.bottom + 110) score += 7;
    if (el.getAttribute('aria-haspopup')) score += 3;
    if (score <= 5) continue;
    if (score > bestScore) { bestScore = score; best = el; }
  }
  if (!best) return 'notfound';
  var br = best.getBoundingClientRect();
  var label = txt(best).slice(0, 40);
  try { best.click(); } catch(e) {}
  return 'ok:' + Math.round(br.left + br.width / 2) + ':' + Math.round(br.top + br.height / 2) + ':' + label;
})();
"""

    /** ROUND 15.3 -- choose "1" (one image per run) inside the chip menu that OPEN_COUNT_MENU opened. */
    val PICK_ONE_IMAGE = """
(function(cx, cy){
  cx = Number(cx) || 0; cy = Number(cy) || 0;
  var els = document.querySelectorAll('[role="menuitem"],[role="option"],li,button,[role="button"],div[role="menuitemradio"]');
  var cands = [];
  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    if (!el || el.disabled) continue;
    var r = el.getBoundingClientRect();
    if (!r || r.width < 18 || r.height < 12 || r.height > 220) continue;
    if (r.bottom < 0 || r.top > (window.innerHeight || 3000)) continue;
    var t = ((el.innerText || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' +
             (el.getAttribute('title') || '')).replace(/\s+/g, ' ').trim();
    if (!t || t.length > 30) continue;
    var low = t.toLowerCase();
    var score = 0;
    if (low === '1' || low === 'x1' || low === 'one') score += 14;
    if (/^1\s?(image|photo|picture|result)s?/.test(low)) score += 12;
    if (/(one image|1 image|x1|only one)/.test(low)) score += 10;
    if (score === 0) continue;
    if (el.closest('[role="menu"],[role="listbox"],[role="dialog"],[role="menu-item"]')) score += 8;
    var dx = Math.abs((r.left + r.width / 2) - cx);
    var dy = Math.abs((r.top + r.height / 2) - cy);
    if (dx < 300 && dy < 420) score += 6;
    cands.push({ el: el, t: t.slice(0, 24), score: score });
  }
  cands.sort(function(a, b){ return b.score - a.score; });
  if (!cands.length) return 'notfound';
  try { cands[0].el.click(); } catch(e) {}
  return 'ok:' + cands[0].t;
})(%1, %2);
"""

    /** ROUND 15.3 -- scroll the composer into view, drop IME focus and un-block stray "can make mistakes" banners before the real tap. */
    val PREPARE_SEND = """
(function(){
  var S = window.__saifFlow || {};
  var c = S.composer ? S.composer() : null;
  if (!c) return 'nocomposer';
  try { c.scrollIntoView({ block: 'center', inline: 'nearest' }); } catch(e) {}
  try {
    if (document.activeElement && document.activeElement !== document.body && document.activeElement.blur) {
      document.activeElement.blur();
    }
  } catch(e) {}
  try { c.blur(); } catch(e) {}
  try {
    var junk = document.querySelectorAll('[role="status"],[aria-live="polite"],[aria-live="assertive"]');
    for (var i = 0; i < junk.length; i++) {
      var t = (junk[i].innerText || '').toLowerCase();
      if (t.indexOf('mistake') >= 0 || t.indexOf('beta') >= 0 || t.indexOf('experiment') >= 0) {
        junk[i].style.pointerEvents = 'none';
      }
    }
  } catch(e) {}
  var r = c.getBoundingClientRect();
  return (r && r.width > 40) ? 'ok' : 'hidden';
})();
"""

    /** ROUND 15.3 -- open Flow settings (aspect ratio / output) so SET_ASPECT can pick the ratio the owner chose. */
    val OPEN_SETTINGS = """
(function(){
  var els = document.querySelectorAll('button,[role="button"],[aria-haspopup],[aria-label],[title]');
  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    if (!el || el.disabled) continue;
    var r = el.getBoundingClientRect();
    if (!r || r.width < 12 || r.height < 12 || r.width > 400) continue;
    if (r.top < 0 || r.bottom > (window.innerHeight || 4000)) continue;
    var t = ((el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '') + ' ' +
             ((el.innerText || '').slice(0, 60))).toLowerCase().replace(/\s+/g, ' ').trim();
    if (/setting|aspect|ratio|size|format|output/.test(t)) {
      try { el.click(); } catch(e) {}
      return 'ok:' + t.slice(0, 32);
    }
  }
  return 'notfound';
})();
"""

    /** ROUND 15.3 -- close any popup/menu we opened, without touching the composer text. */
    val CLOSE_MENUS = """
(function(){
  try {
    ['keydown', 'keyup'].forEach(function(k){
      document.body.dispatchEvent(new KeyboardEvent(k, { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true }));
    });
  } catch(e) {}
  return 'ok';
})();
"""

    /** ROUND 15.3 -- read the model/output chip text (used to verify "1 image" really got picked). */
    val CHIP_STATE = """
(function(){
  var S = window.__saifFlow || {};
  var c = S.composer ? S.composer() : null;
  if (!c) return 'nocomposer';
  var cr = c.getBoundingClientRect();
  var els = document.querySelectorAll('button,[role="button"],div[role="button"],span[role="button"]');
  var out = [];
  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    var r = el.getBoundingClientRect();
    if (!r || r.width < 20 || r.height < 12 || r.width > 320) continue;
    if (r.top < cr.top - 120 || r.bottom > cr.bottom + 120) continue;
    var t = (el.innerText || '').replace(/\s+/g, ' ').trim();
    if (!t || t.length > 40) continue;
    out.push(t);
  }
  return out.slice(0, 6).join(' | ');
})();
"""

    /** ROUND 15.4 -- start a BRAND-NEW Flow project, so pictures from older projects can never be picked up. */
    val CREATE_PROJECT = """
(function(home){
  var S = window.__saifFlow || {};
  function txt(el){
    return ((el.innerText || el.textContent || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' +
            (el.getAttribute('title') || '') + ' ' + (el.getAttribute('data-testid') || ''))
            .toLowerCase().replace(/\s+/g, ' ').trim();
  }
  function vis(el){
    try {
      var r = el.getBoundingClientRect();
      return !!r && r.width > 18 && r.height > 12 && r.bottom > 0;
    } catch(e) { return false; }
  }
  // 1. THIS page is already a brand-new, empty project (composer present, no result pictures).
  var c = S.composer ? S.composer() : null;
  var results = 0;
  var imgs = document.querySelectorAll('img,video');
  for (var i = 0; i < imgs.length; i++) {
    var im = imgs[i];
    var w = im.naturalWidth || im.videoWidth || 0;
    var h = im.naturalHeight || im.videoHeight || 0;
    if (w >= 300 && h >= 300 && vis(im)) results++;
  }
  if (c && results === 0) return 'ok:fresh-project';
  // 2. Click a "new project" style control on whatever page we are on.
  var rx = /(new project|create project|start a project|blank project|new video|create video|start creating|naya project)/i;
  var els = document.querySelectorAll('button,a,[role="button"],[aria-label],[data-testid]');
  for (var j = 0; j < els.length; j++) {
    var el = els[j];
    if (!el || el.disabled || !vis(el)) continue;
    var t = txt(el);
    if (!t || t.length > 52) continue;
    if (/(delete|remove|rename|duplicate|share)/.test(t)) continue;
    if (!rx.test(t)) continue;
    try { el.click(); return 'ok:clicked:' + t.slice(0, 30); } catch(e) {}
  }
  // 3. Nothing clickable here — go back to Flow home, where the "New project" tile lives.
  try {
    if (home && location.href.indexOf(home) !== 0) { location.href = home; return 'navigating'; }
  } catch(e) {}
  return 'notfound';
})(%s);
"""

    val CLEAR_STORAGE = """
(function(){
  try { window.localStorage.clear(); } catch(e) {}
  try { window.sessionStorage.clear(); } catch(e) {}
  try { if (window.indexedDB && indexedDB.databases) { indexedDB.databases().then(function(dbs){ dbs.forEach(function(d){ if (d && d.name) indexDBDelete(d.name); }); }); } } catch(e) {}
  function indexDBDelete(n){ try { indexedDB.deleteDatabase(n); } catch(e) {} }
  return 'ok';
})();
"""
}