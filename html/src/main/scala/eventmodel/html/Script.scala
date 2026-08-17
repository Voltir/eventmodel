package eventmodel.html

/** The only JavaScript in the project. Kept small and hand-written on purpose:
  * this is a site you read, not an app.
  *
  * It does three things -- filtering and folding, drawing the arrows between
  * sticky notes, and live reload. The same script is inlined into every page
  * but only the storyboard has a toolbar or a grid, so every lookup is guarded.
  *
  * The arrows are the one piece that needs geometry, and it is deliberately
  * measured from the laid-out DOM rather than computed in Scala: the grid sizes
  * itself to its content, and nothing on the server knows how wide a card ends
  * up. With JS off there are simply no arrows and the board still reads.
  */
object Script:
  val js: String =
    """
(function () {
  var filter = document.getElementById('filter');
  var toggleFields = document.getElementById('toggle-fields');
  var expandRules = document.getElementById('expand-rules');
  var model = document.querySelector('.model');

  // --- filtering -----------------------------------------------------------
  // data-slices is a list, because a screen shared by consecutive slices is
  // drawn once and belongs to all of them.
  function matches(el, q) {
    if (q === '') return true;
    var names = (el.getAttribute('data-slices') || '').split('|');
    for (var i = 0; i < names.length; i++) {
      if (names[i].trim().indexOf(q) !== -1) return true;
    }
    return false;
  }

  if (filter) {
    filter.addEventListener('input', function () {
      var q = filter.value.trim().toLowerCase();
      document.querySelectorAll('[data-slices]').forEach(function (el) {
        el.hidden = !matches(el, q);
      });
      relayout();
    });
  }

  if (toggleFields) {
    toggleFields.addEventListener('click', function () {
      document.body.classList.toggle('hide-fields');
      relayout();
    });
  }

  if (expandRules) {
    expandRules.addEventListener('click', function () {
      var rules = document.querySelectorAll('details.rule');
      var anyClosed = Array.prototype.some.call(rules, function (r) { return !r.open; });
      rules.forEach(function (r) { r.open = anyClosed; });
      expandRules.textContent = anyClosed ? 'Collapse all rules' : 'Expand all rules';
    });
  }

  // --- arrows --------------------------------------------------------------
  var NS = 'http://www.w3.org/2000/svg';
  var edges = [];
  var blob = document.getElementById('em-edges');
  if (blob) { try { edges = JSON.parse(blob.textContent) || []; } catch (e) { edges = []; } }

  var svg = null;
  var paths = [];

  if (model && edges.length) {
    svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('class', 'edges');
    svg.innerHTML =
      '<defs>' +
      '<marker id="em-arrow" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="5" markerHeight="5" orient="auto">' +
        '<path d="M0,0 L8,4 L0,8 z" class="arrowhead"/></marker>' +
      '<marker id="em-arrow-far" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="5" markerHeight="5" orient="auto">' +
        '<path d="M0,0 L8,4 L0,8 z" class="arrowhead far"/></marker>' +
      '</defs>';
    model.appendChild(svg);

    edges.forEach(function (e) {
      var p = document.createElementNS(NS, 'path');
      p.setAttribute('class', 'edge' + (e.far ? ' far' : '') + (e.branch ? ' branch' : ''));
      p.setAttribute('marker-end', e.far ? 'url(#em-arrow-far)' : 'url(#em-arrow)');
      svg.appendChild(p);
      paths.push({ el: p, edge: e });
    });
  }

  // Measured against the SVG's own box, which is the coordinate origin its
  // user units are expressed in -- the grid's padding shifts it off the
  // element rect otherwise.
  function boxOf(id, base) {
    var el = document.getElementById(id);
    if (!el) return null;
    var r = el.getBoundingClientRect();
    if (!r.width || !r.height) return null;  // filtered out of the view
    return { id: id, x: r.left - base.left, y: r.top - base.top, w: r.width, h: r.height };
  }

  function clamp(v, lo, hi) { return Math.min(Math.max(v, lo), hi); }

  // Every card, measured once per layout, so a connector can be asked whether
  // anything is actually in its way rather than guessing from the distance.
  var cards = [];
  function measureCards(base) {
    cards = [];
    model.querySelectorAll('.card').forEach(function (el) {
      var r = el.getBoundingClientRect();
      if (!r.width || !r.height) return;
      cards.push({ id: el.id, x: r.left - base.left, y: r.top - base.top, w: r.width, h: r.height });
    });
  }

  // Near connectors are always vertical -- both ends sit in one column -- so a
  // box test against the segment is exact.
  function blocked(x, y0, y1, from, to) {
    var lo = Math.min(y0, y1), hi = Math.max(y0, y1);
    for (var i = 0; i < cards.length; i++) {
      var c = cards[i];
      if (c.id === from || c.id === to) continue;
      if (x > c.x && x < c.x + c.w && lo < c.y + c.h && hi > c.y) return true;
    }
    return false;
  }

  /** A polyline with the corners rounded off, which reads far better than
    * right angles at this size. */
  function rounded(pts, r) {
    var d = 'M' + pts[0][0] + ',' + pts[0][1];
    for (var i = 1; i < pts.length - 1; i++) {
      var p = pts[i - 1], c = pts[i], n = pts[i + 1];
      var d1 = Math.hypot(c[0] - p[0], c[1] - p[1]);
      var d2 = Math.hypot(n[0] - c[0], n[1] - c[1]);
      if (!d1 || !d2) continue;
      var rr = Math.min(r, d1 / 2, d2 / 2);
      d += ' L' + (c[0] + (p[0] - c[0]) / d1 * rr) + ',' + (c[1] + (p[1] - c[1]) / d1 * rr) +
           ' Q' + c[0] + ',' + c[1] +
           ' ' + (c[0] + (n[0] - c[0]) / d2 * rr) + ',' + (c[1] + (n[1] - c[1]) / d2 * rr);
    }
    var last = pts[pts.length - 1];
    return d + ' L' + last[0] + ',' + last[1];
  }

  function nearPath(a, b) {
    var ax = a.x + a.w / 2, bx = b.x + b.w / 2;
    // A screen shared by several slices is one wide note. Leave it from
    // directly above whatever it connects to, not from its midpoint.
    if (a.w > b.w * 1.6) ax = clamp(bx, a.x + 14, a.x + a.w - 14);
    if (b.w > a.w * 1.6) bx = clamp(ax, b.x + 14, b.x + b.w - 14);

    var down = b.y >= a.y;
    var sy = down ? a.y + a.h : a.y;
    var ty = down ? b.y : b.y + b.h;

    if (!blocked(ax, sy, ty, a.id, b.id)) return 'M' + ax + ',' + sy + ' L' + bx + ',' + ty;

    // Something is in the way -- an automation climbing from its processor back
    // up to its command passes its own read model and an event. Route it up the
    // channel beside the column, which is what the column gap is there for.
    // Measured off the narrower card: a wide screen note spans several columns,
    // so its own right edge is nowhere near this one.
    var col = a.w <= b.w ? a : b;
    var gx = col.x + col.w + 14;
    var step = down ? 12 : -12;
    return rounded(
      [[ax, sy], [ax, sy + step], [gx, sy + step], [gx, ty - step], [bx, ty - step], [bx, ty]],
      7
    );
  }

  function farPath(a, b) {
    // Reaching back along the timeline: leave vertically, arrive from the side.
    var ax = a.x + a.w / 2;
    var rightwards = b.x > a.x;
    var tx = rightwards ? b.x : b.x + b.w;
    var ty = b.y + b.h / 2;
    var down = b.y >= a.y;
    var sy = down ? a.y + a.h : a.y;
    var lead = down ? 46 : -46;
    return 'M' + ax + ',' + sy +
           ' C' + ax + ',' + (sy + lead) +
           ' ' + (tx + (rightwards ? -70 : 70)) + ',' + ty +
           ' ' + tx + ',' + ty;
  }

  function relayout() {
    if (!svg) return;
    var base = svg.getBoundingClientRect();
    measureCards(base);
    paths.forEach(function (p) {
      var a = boxOf(p.edge.from, base);
      var b = boxOf(p.edge.to, base);
      if (!a || !b) { p.el.setAttribute('d', ''); return; }
      p.el.setAttribute('d', p.edge.far ? farPath(a, b) : nearPath(a, b));
    });
  }

  var queued = false;
  function scheduleRelayout() {
    if (queued) return;
    queued = true;
    requestAnimationFrame(function () { queued = false; relayout(); });
  }

  // --- focus ---------------------------------------------------------------
  // At rest only the short connectors show. Focusing a slice fans in the long
  // arrows that say where its data actually comes from.
  var pinned = null;
  var active = null;

  function endpointsOf(slug) {
    var ids = {};
    edges.forEach(function (e) {
      if (e.slice === slug) { ids[e.from] = 1; ids[e.to] = 1; }
    });
    return ids;
  }

  function applyFocus(slug) {
    if (!model) return;
    var ids = endpointsOf(slug);
    model.classList.add('focusing');
    model.querySelectorAll('.cell, .slice-head').forEach(function (c) {
      var own = (c.getAttribute('data-slice-ids') || '').split(' ').indexOf(slug) !== -1;
      var hit = own;
      if (!hit) {
        var kids = c.querySelectorAll('[id]');
        for (var i = 0; i < kids.length && !hit; i++) { if (ids[kids[i].id]) hit = true; }
      }
      c.classList.toggle('lit', hit);
    });
    paths.forEach(function (p) { p.el.classList.toggle('lit', p.edge.slice === slug); });
  }

  function clearFocus() {
    if (!model) return;
    active = null;
    model.classList.remove('focusing');
    model.querySelectorAll('.lit').forEach(function (el) { el.classList.remove('lit'); });
    paths.forEach(function (p) { p.el.classList.remove('lit'); });
  }

  if (model && edges.length) {
    model.addEventListener('mouseover', function (ev) {
      if (pinned) return;
      var host = ev.target.closest('[data-slice-ids]');
      if (!host) return;
      var ids = host.getAttribute('data-slice-ids').split(' ');
      // A cell shared by several slices -- the wide screen note -- belongs to
      // all of them, so hovering it should not pick one arbitrarily.
      if (ids.length !== 1) return;
      if (ids[0] === active) return;
      active = ids[0];
      applyFocus(active);
    });

    model.addEventListener('mouseleave', function () { if (!pinned) clearFocus(); });

    model.addEventListener('click', function (ev) {
      var head = ev.target.closest('.slice-head');
      if (!head || ev.target.closest('a')) return;  // let the slice links work
      var slug = head.getAttribute('data-slice-ids');
      model.querySelectorAll('.slice-head.pinned').forEach(function (h) {
        h.classList.remove('pinned');
      });
      if (pinned === slug) { pinned = null; clearFocus(); }
      else { pinned = slug; head.classList.add('pinned'); applyFocus(slug); }
    });

    window.addEventListener('resize', scheduleRelayout);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(relayout);
    scheduleRelayout();
  }

  // --- live reload ---------------------------------------------------------
  // Restore where you were, so a re-render doesn't throw away your place in a
  // storyboard you had scrolled sideways.
  var KEY = 'em-scroll:' + location.pathname;
  try {
    var saved = JSON.parse(sessionStorage.getItem(KEY) || 'null');
    if (saved) {
      window.scrollTo(saved.x, saved.y);
      document.querySelectorAll('.scroller').forEach(function (el, i) {
        if (saved.lanes && saved.lanes[i] != null) el.scrollLeft = saved.lanes[i];
      });
      sessionStorage.removeItem(KEY);
    }
  } catch (e) {}

  function rememberPosition() {
    var lanes = [];
    document.querySelectorAll('.scroller').forEach(function (el) { lanes.push(el.scrollLeft); });
    try {
      sessionStorage.setItem(KEY, JSON.stringify({ x: window.scrollX, y: window.scrollY, lanes: lanes }));
    } catch (e) {}
  }

  // Inert on file:// -- fetch is blocked there, and polling a URL that can
  // never succeed would just log an error every second.
  var meta = document.querySelector('meta[name="em-build"]');
  if (meta && location.protocol.indexOf('http') === 0) {
    var current = meta.getAttribute('content');
    var stampUrl = meta.getAttribute('data-url');
    setInterval(function () {
      fetch(stampUrl, { cache: 'no-store' })
        .then(function (r) { return r.ok ? r.text() : null; })
        .then(function (text) {
          if (text && text.trim() !== current) {
            rememberPosition();
            location.reload();
          }
        })
        .catch(function () { /* renderer mid-write, or server down */ });
    }, 1000);
  }
})();
"""
