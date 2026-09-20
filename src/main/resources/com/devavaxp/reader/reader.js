/*
 * Devava Reader — pagination engine.
 *
 * Turns the XHTML document of a chapter into "views" of one or two columns (pages)
 * and moves between them without losing or clipping any content.
 *
 * Principles (verified empirically against the WebKit build shipped with JavaFX):
 *  - The <body> is the multi-column container, with visible overflow; <html> clips.
 *  - The effective width (We) is a multiple of the column count, so the column pitch
 *    (We / columns) is an exact integer and every view starts on a whole pixel.
 *  - Views are moved with transform: translateX(-view * We). scrollWidth is NEVER
 *    measured while the transform is applied (this WebKit subtracts it), so every
 *    measurement is taken with transform: none.
 *  - The column count is obtained by rounding scrollWidth to the column pitch, which
 *    tolerates any sub-pixel deviation: columns = round((scrollWidth + padH) / pitch),
 *    where padH is the effective horizontal padding of the body (in single-column mode
 *    it may be larger than marginH to limit the line length and center the text).
 *
 * Java talks to this engine through window.__reader, and the engine notifies Java
 * through window.readerBridge (a Java object injected by the WebView).
 */
(function () {
  'use strict';

  if (window.__reader) {
    return;
  }

  var cfg = {
    columns: 2,
    marginH: 48,
    marginV: 36,
    fontSize: 20,
    lineHeight: 1.6,
    fontFamily: 'Georgia, "Times New Roman", "Noto Serif", "Liberation Serif", serif',
    background: '#FAFAF9',
    textColor: '#1C1917',
    linkColor: '#2563EB',
    forceColors: false,
    edgeClicks: true
  };

  var geo = { W: 0, H: 0, We: 0, pitch: 0, columnWidth: 0, columnHeight: 0, gap: 0, padH: 0 };
  var state = { view: 0, total: 1, columns: 0 };
  var styleEl = null;
  var relayoutTimer = null;

  // ------------------------------------------------------------------
  // Communication with Java
  // ------------------------------------------------------------------

  function bridge() {
    return window.readerBridge || null;
  }

  function notify(event, data) {
    var b = bridge();
    if (b && typeof b.onEvent === 'function') {
      try {
        b.onEvent(String(event), data == null ? '' : String(data));
      } catch (e) {
        // The bridge may be unavailable while the document is being unloaded.
      }
    }
  }

  // ------------------------------------------------------------------
  // Geometry and styles
  // ------------------------------------------------------------------

  /**
   * Pagination geometry. Invariant: pitch = columnWidth + gap and gap = 2 * padH,
   * so every view (cols columns) is exactly We pixels wide and view v starts at v * We.
   */
  function computeGeometry() {
    var cols = cfg.columns === 1 ? 1 : 2;
    geo.W = Math.max(1, window.innerWidth);
    geo.H = Math.max(1, window.innerHeight);
    geo.We = geo.W - (geo.W % cols);
    geo.padH = cfg.marginH;
    if (cols === 1) {
      // Overly long lines are tiring: cap the measure at ~40em and center it.
      var maxWidth = Math.round(cfg.fontSize * 40);
      var spare = geo.We - 2 * cfg.marginH - maxWidth;
      if (spare > 0) {
        geo.padH = cfg.marginH + Math.floor(spare / 2);
      }
    }
    geo.gap = 2 * geo.padH;
    geo.pitch = geo.We / cols;
    geo.columnWidth = geo.pitch - geo.gap;
    geo.columnHeight = Math.max(50, geo.H - 2 * cfg.marginV);
  }

  function buildCss() {
    var cols = cfg.columns === 1 ? 1 : 2;
    var css = [
      'html{margin:0!important;padding:0!important;width:' + geo.W + 'px!important;height:' + geo.H + 'px!important;',
      'overflow:hidden!important;background:' + cfg.background + '!important;}',
      'html body{display:block!important;margin:0!important;padding:' + cfg.marginV + 'px ' + geo.padH + 'px!important;',
      'box-sizing:border-box!important;width:' + geo.We + 'px!important;height:' + geo.H + 'px!important;',
      'min-height:0!important;max-height:none!important;min-width:0!important;max-width:none!important;',
      'overflow:visible!important;position:static!important;',
      'column-count:' + cols + '!important;column-gap:' + geo.gap + 'px!important;column-fill:auto!important;',
      // With a single column, column-count:1 does NOT create a multi-column context in WebKit
      // (the text would overflow downwards); an explicit column-width forces it.
      'column-rule:none!important;column-width:' + (cols === 1 ? geo.columnWidth + 'px' : 'auto') + '!important;',
      'font-family:' + cfg.fontFamily + '!important;font-size:' + cfg.fontSize + 'px!important;',
      'line-height:' + cfg.lineHeight + '!important;color:' + cfg.textColor + '!important;',
      'background:' + cfg.background + '!important;transform-origin:0 0!important;transition:none!important;',
      'overflow-wrap:break-word!important;word-wrap:break-word!important;-webkit-hyphens:auto;hyphens:auto;}',
      'html body a{color:' + cfg.linkColor + '!important;}',
      'html body img,html body svg,html body video,html body canvas{max-width:100%!important;',
      'max-height:' + geo.columnHeight + 'px!important;object-fit:contain!important;break-inside:avoid;page-break-inside:avoid;}',
      'html body table{max-width:100%!important;}',
      'html body pre{white-space:pre-wrap!important;max-width:100%!important;}',
      'html body ::selection{background:rgba(37,99,235,0.28);}'
    ];
    if (cfg.forceColors) {
      // In the dark theme the EPUB's own colors (usually black text) would be unreadable.
      css.push('html body *:not(a){color:inherit!important;background-color:transparent!important;border-color:' + cfg.textColor + ';}');
    }
    return css.join('');
  }

  function applyStyles() {
    computeGeometry();
    if (!styleEl || !styleEl.parentNode) {
      styleEl = document.createElement('style');
      styleEl.id = '__reader_css';
      (document.head || document.documentElement).appendChild(styleEl);
    }
    styleEl.textContent = buildCss();
  }

  // ------------------------------------------------------------------
  // Measuring and navigation
  // ------------------------------------------------------------------

  function withoutTransform(fn) {
    var b = document.body;
    var previous = b.style.transform;
    b.style.transform = 'none';
    try {
      return fn();
    } finally {
      b.style.transform = previous;
    }
  }

  function columnOfRect(r) {
    return Math.floor((r.left - geo.padH + 2) / geo.pitch);
  }

  /**
   * Last column (0-based) that holds any visible fragment of an element or text node, +1.
   * Used to discard "phantom" columns that WebKit adds to the overflow area because of
   * trailing margins, which would otherwise produce a blank view.
   */
  function columnsWithContent() {
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, null, false);
    var n;
    var max = -1;
    while ((n = walker.nextNode())) {
      var rects;
      if (n.nodeType === 3) {
        if (!n.nodeValue || !n.nodeValue.trim()) continue;
        var rg = document.createRange();
        rg.selectNodeContents(n);
        rects = rg.getClientRects();
      } else {
        if (n === styleEl) continue;
        rects = n.getClientRects();
      }
      for (var i = 0; i < rects.length; i++) {
        var r = rects[i];
        if (r.width <= 0 && r.height <= 0) continue;
        var c = columnOfRect(r);
        if (c > max) max = c;
      }
    }
    return max + 1;
  }

  /**
   * An image that does not fit in what is left of its column (for example a full-page
   * illustration preceded by a margin) gets "sliced" by WebKit across two columns.
   * Fragmented images are detected here and shrunk to the available space.
   */
  function fitImages() {
    var images = document.images || [];
    var i;
    for (i = 0; i < images.length; i++) {
      if (images[i].hasAttribute('data-reader-fitted')) {
        images[i].style.removeProperty('max-height');
        images[i].removeAttribute('data-reader-fitted');
      }
    }
    for (var pass = 0; pass < 3; pass++) {
      var changed = false;
      for (i = 0; i < images.length; i++) {
        var img = images[i];
        var rects = img.getClientRects();
        if (rects.length <= 1) continue;
        var offset = Math.max(0, rects[0].top - cfg.marginV);
        var available = Math.floor(geo.columnHeight - offset) - 1;
        if (available < 48) continue; // no reasonable room: leave it as is
        img.style.setProperty('max-height', available + 'px', 'important');
        img.setAttribute('data-reader-fitted', '1');
        changed = true;
      }
      if (!changed) break;
    }
  }

  function measure() {
    var cols = cfg.columns === 1 ? 1 : 2;
    var columns = withoutTransform(function () {
      fitImages();
      var totalWidth = document.body.scrollWidth;
      var byScroll = Math.max(1, Math.round((totalWidth + geo.padH) / geo.pitch));
      var byContent = columnsWithContent();
      return byContent > 0 ? Math.min(byScroll, byContent) : byScroll;
    });
    state.columns = columns;
    state.total = Math.max(1, Math.ceil(columns / cols));
    if (state.view > state.total - 1) {
      state.view = state.total - 1;
    }
  }

  function goTo(v) {
    v = Math.floor(Number(v) || 0);
    v = Math.max(0, Math.min(state.total - 1, v));
    state.view = v;
    document.body.style.transform = v === 0 ? 'none' : 'translateX(-' + (v * geo.We) + 'px)';
  }

  function stateJson() {
    return JSON.stringify({ view: state.view, total: state.total, columns: state.columns });
  }

  /** View (0..total-1) that contains a rectangle measured without the transform. */
  function viewOfRect(r) {
    if (!r) return -1;
    var cols = cfg.columns === 1 ? 1 : 2;
    var x = r.left;
    if (r.width === 0 && r.height === 0 && x === 0) return -1;
    var col = Math.floor((x - geo.padH + 2) / geo.pitch);
    col = Math.max(0, Math.min(state.columns - 1, col));
    return Math.floor(col / cols);
  }

  function rectOfRange(range) {
    var rects = range.getClientRects();
    for (var i = 0; i < rects.length; i++) {
      if (rects[i].width || rects[i].height) return rects[i];
    }
    var r = range.getBoundingClientRect();
    return (r && (r.width || r.height || r.left || r.top)) ? r : null;
  }

  function viewOfElement(el) {
    return withoutTransform(function () {
      var range = document.createRange();
      try {
        range.selectNode(el);
      } catch (e) {
        return -1;
      }
      var r = rectOfRange(range);
      if (!r) {
        r = el.getBoundingClientRect();
      }
      return viewOfRect(r);
    });
  }

  // ------------------------------------------------------------------
  // Position anchor: brings the same text back after a change of window
  // size, font size or column count.
  // ------------------------------------------------------------------

  function currentAnchor() {
    var b = document.body;
    if (!b) return null;
    var x = geo.padH + 4;
    if (document.caretRangeFromPoint) {
      for (var y = cfg.marginV + 4; y < geo.H - cfg.marginV; y += 32) {
        var range = null;
        try {
          range = document.caretRangeFromPoint(x, y);
        } catch (e) {
          range = null;
        }
        if (range && range.startContainer && b.contains(range.startContainer) && range.startContainer !== b) {
          return { node: range.startContainer, offset: range.startOffset };
        }
      }
    }
    // Fallback: the first node with content inside the current view.
    return withoutTransform(function () {
      var start = state.view * geo.We;
      var end = start + geo.We;
      var walker = document.createTreeWalker(b, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, null, false);
      var n;
      while ((n = walker.nextNode())) {
        var r = null;
        if (n.nodeType === 3) {
          if (!n.nodeValue || !n.nodeValue.trim()) continue;
          var rg = document.createRange();
          rg.selectNodeContents(n);
          var rects = rg.getClientRects();
          for (var i = 0; i < rects.length; i++) {
            if (rects[i].width && rects[i].left >= start - 1 && rects[i].left < end) {
              return { node: n, offset: 0 };
            }
          }
          continue;
        }
        if (n.nodeType === 1 && /^(img|svg|video|canvas)$/i.test(n.tagName)) {
          r = n.getBoundingClientRect();
          if (r && r.width && r.left >= start - 1 && r.left < end) {
            return { node: n, offset: 0 };
          }
        }
      }
      return null;
    });
  }

  function goToAnchor(anchor) {
    if (!anchor || !anchor.node || !document.body.contains(anchor.node)) return false;
    var v = withoutTransform(function () {
      var range = document.createRange();
      var n = anchor.node;
      try {
        if (n.nodeType === 3) {
          var len = n.nodeValue.length;
          var off = Math.max(0, Math.min(anchor.offset || 0, len));
          range.setStart(n, off);
          range.setEnd(n, Math.min(off + 1, len));
        } else if (n.childNodes && n.childNodes.length && anchor.offset < n.childNodes.length) {
          range.selectNode(n.childNodes[anchor.offset]);
        } else {
          range.selectNodeContents(n);
        }
      } catch (e) {
        return -1;
      }
      return viewOfRect(rectOfRange(range));
    });
    if (v < 0) return false;
    goTo(v);
    return true;
  }

  function relayout(keepPosition) {
    var anchor = keepPosition ? currentAnchor() : null;
    applyStyles();
    measure();
    if (!(anchor && goToAnchor(anchor))) {
      goTo(state.view);
    }
    notify('state', stateJson());
  }

  function scheduleRelayout() {
    clearTimeout(relayoutTimer);
    relayoutTimer = setTimeout(function () {
      relayout(true);
    }, 80);
  }

  // ------------------------------------------------------------------
  // Document events
  // ------------------------------------------------------------------

  function linkFrom(target) {
    var t = target;
    while (t && t !== document) {
      if (t.nodeType === 1 && String(t.tagName).toLowerCase() === 'a' && t.getAttribute('href')) {
        return t;
      }
      t = t.parentNode;
    }
    return null;
  }

  function installEvents() {
    window.addEventListener('resize', scheduleRelayout);

    if (document.fonts && document.fonts.ready && typeof document.fonts.ready.then === 'function') {
      document.fonts.ready.then(scheduleRelayout, function () {});
    }

    var images = document.images || [];
    for (var i = 0; i < images.length; i++) {
      if (!images[i].complete) {
        images[i].addEventListener('load', scheduleRelayout);
        images[i].addEventListener('error', scheduleRelayout);
      }
    }

    document.addEventListener('click', function (ev) {
      if (ev.button !== 0) return;
      var link = linkFrom(ev.target);
      if (link) {
        ev.preventDefault();
        ev.stopPropagation();
        var b = bridge();
        if (b && typeof b.onLink === 'function') {
          try {
            b.onLink(String(link.href || ''), String(link.getAttribute('href') || ''));
          } catch (e) {
            // without a bridge there is nowhere to navigate
          }
        }
        return;
      }
      if (!cfg.edgeClicks) return;
      var sel = window.getSelection ? window.getSelection() : null;
      if (sel && String(sel).length > 0) return;
      var x = ev.clientX;
      if (x < geo.W * 0.15) {
        notify('previous', '');
      } else if (x > geo.W * 0.85) {
        notify('next', '');
      }
    }, true);

    // Neither the context menu nor image dragging adds anything to a reader.
    document.addEventListener('contextmenu', function (ev) { ev.preventDefault(); }, true);
    document.addEventListener('dragstart', function (ev) { ev.preventDefault(); }, true);
  }

  // ------------------------------------------------------------------
  // Search: the same normalization as BookSearch in Java (lower case, no diacritics,
  // plain quotes and dashes), one output character per input character or none.
  // ------------------------------------------------------------------

  function normalizeForSearch(text) {
    var out = '';
    for (var i = 0; i < text.length; i++) {
      var c = text.charAt(i);
      var code = c.charCodeAt(0);
      if (code >= 0x300 && code <= 0x36f) continue; // combining mark
      if (code > 127 && typeof c.normalize === 'function') {
        var d = c.normalize('NFD');
        if (d.length) c = d.charAt(0);
        code = c.charCodeAt(0);
        if (code >= 0x300 && code <= 0x36f) continue;
      }
      switch (c) {
        case '\u2018': case '\u2019': case '\u201A': case '\u2032': case '\u00B4': case '`': c = "'"; break;
        case '\u201C': case '\u201D': case '\u201E': case '\u2033': case '\u00AB': case '\u00BB': c = '"'; break;
        case '\u2010': case '\u2011': case '\u2012': case '\u2013': case '\u2014': c = '-'; break;
        case '\u00A0': c = ' '; break;
        default: c = c.toLowerCase();
      }
      out += c;
    }
    return out;
  }

  // ------------------------------------------------------------------
  // Public API (used from Java)
  // ------------------------------------------------------------------

  var R = {
    /** Applies a configuration (JSON) and recomputes. keep=true tries to preserve the position. */
    configure: function (json, keep) {
      var o = {};
      try {
        o = typeof json === 'string' ? JSON.parse(json) : (json || {});
      } catch (e) {
        o = {};
      }
      for (var k in o) {
        if (Object.prototype.hasOwnProperty.call(o, k)) cfg[k] = o[k];
      }
      relayout(keep === true);
      return stateJson();
    },
    state: stateJson,
    geometry: function () {
      return JSON.stringify(geo);
    },
    fraction: function () {
      return state.total > 1 ? state.view / (state.total - 1) : 0;
    },
    goTo: function (v) { goTo(v); return stateJson(); },
    goToStart: function () { goTo(0); return stateJson(); },
    goToEnd: function () { goTo(state.total - 1); return stateJson(); },
    goToFraction: function (f) {
      f = Math.max(0, Math.min(1, Number(f) || 0));
      goTo(Math.round(f * (state.total - 1)));
      return stateJson();
    },
    next: function () {
      if (state.view < state.total - 1) {
        goTo(state.view + 1);
        return true;
      }
      return false;
    },
    previous: function () {
      if (state.view > 0) {
        goTo(state.view - 1);
        return true;
      }
      return false;
    },
    /** Goes to the view containing the element with that id (or name). Returns false if it does not exist. */
    goToFragment: function (id) {
      if (!id) return false;
      var el = document.getElementById(id);
      if (!el && document.getElementsByName) {
        var byName = document.getElementsByName(id);
        if (byName && byName.length) el = byName[0];
      }
      if (!el) return false;
      var v = viewOfElement(el);
      if (v < 0) return false;
      goTo(v);
      return true;
    },
    remeasure: function () {
      relayout(true);
      return stateJson();
    },
    /**
     * Goes to an occurrence of `term` (the text of a search hit) and selects it. Among the
     * occurrences in the document, the one whose surroundings best match `before` and
     * `after` (the hit's context) is chosen, so the same hit is found again even though the
     * text was searched outside the browser. Returns false when the term is not found.
     */
    goToMatch: function (term, before, after) {
      var b = document.body;
      if (!b) return false;
      var needle = normalizeForSearch(String(term || ''));
      if (!needle) return false;
      var ctxBefore = normalizeForSearch(String(before || ''));
      var ctxAfter = normalizeForSearch(String(after || ''));

      // Concatenate the text nodes (like ChapterText does in Java), remembering where each
      // normalized character comes from.
      var walker = document.createTreeWalker(b, NodeFilter.SHOW_TEXT, null, false);
      var hay = '';
      var nodes = [];
      var offsets = [];
      var n;
      while ((n = walker.nextNode())) {
        var skip = false;
        for (var p = n.parentNode; p && p !== b; p = p.parentNode) {
          if (p.nodeType === 1 && /^(script|style)$/i.test(p.tagName || '')) { skip = true; break; }
        }
        if (skip) continue;
        var value = n.nodeValue || '';
        for (var i = 0; i < value.length; i++) {
          var c = normalizeForSearch(value.charAt(i)).charAt(0);
          if (!c) continue;
          if (/\s/.test(c)) {
            // Whitespace runs count as one space, as in the text searched in Java.
            if (!hay.length || hay.charAt(hay.length - 1) === ' ') continue;
            c = ' ';
          }
          hay += c;
          nodes.push(n);
          offsets.push(i);
        }
      }

      var best = -1;
      var bestScore = -1;
      var at = hay.indexOf(needle);
      while (at >= 0) {
        var score = 0;
        var k;
        for (k = 1; k <= ctxBefore.length && at - k >= 0; k++) {
          if (hay.charAt(at - k) !== ctxBefore.charAt(ctxBefore.length - k)) break;
          score++;
        }
        for (k = 0; k < ctxAfter.length && at + needle.length + k < hay.length; k++) {
          if (hay.charAt(at + needle.length + k) !== ctxAfter.charAt(k)) break;
          score++;
        }
        if (score > bestScore) { bestScore = score; best = at; }
        at = hay.indexOf(needle, at + 1);
      }
      if (best < 0) return false;

      var range = document.createRange();
      var last = best + needle.length - 1;
      try {
        range.setStart(nodes[best], offsets[best]);
        range.setEnd(nodes[last], offsets[last] + 1);
      } catch (e) {
        return false;
      }
      var v = withoutTransform(function () { return viewOfRect(rectOfRange(range)); });
      if (v < 0) return false;
      goTo(v);
      try {
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
      } catch (e2) {
        // The selection is only a visual aid.
      }
      return true;
    },
    /**
     * First words of the text shown in the current view (for bookmarks), at most maxChars.
     * Headings at the very start are skipped (the bookmark list already says the chapter)
     * and the text starts at a word boundary.
     */
    excerpt: function (maxChars) {
      var limit = Math.max(20, Number(maxChars) || 120);
      var b = document.body;
      var anchor = currentAnchor();
      if (!b || !anchor || !anchor.node) return '';
      var start = anchor.node;
      var offset = anchor.offset || 0;
      if (start.nodeType !== 3) {
        var inner = document.createTreeWalker(start, NodeFilter.SHOW_TEXT, null, false);
        start = inner.nextNode();
        offset = 0;
        if (!start) return '';
      }
      function insideTag(node, re) {
        for (var p = node.parentNode; p && p !== b; p = p.parentNode) {
          if (p.nodeType === 1 && re.test(p.tagName || '')) return true;
        }
        return false;
      }
      var collect = function (skipHeadings) {
        var walker = document.createTreeWalker(b, NodeFilter.SHOW_TEXT, null, false);
        walker.currentNode = start;
        var text = '';
        var n = start;
        var first = true;
        while (n && text.replace(/\s+/g, ' ').length < limit + 20) {
          if (!insideTag(n, /^(script|style)$/i) && !(skipHeadings && !text.trim() && insideTag(n, /^h[1-6]$/i))) {
            var v = n.nodeValue || '';
            if (first && offset > 0) {
              v = v.substring(offset);
              // Mid-word anchor (the caret sits at a line break): start at the next word.
              if (/\S/.test(n.nodeValue.charAt(offset - 1))) v = v.replace(/^\S*\s*/, '');
            }
            text += ' ' + v;
          }
          first = false;
          n = walker.nextNode();
        }
        return text.replace(/\s+/g, ' ').trim();
      };
      var text = collect(true);
      if (!text) text = collect(false);
      if (text.length > limit) {
        text = text.substring(0, limit).replace(/\s+\S*$/, '') + '…';
      }
      return text;
    }
  };

  window.__reader = R;
  installEvents();
})();
