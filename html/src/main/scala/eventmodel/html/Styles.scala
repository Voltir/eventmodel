package eventmodel.html

/** Kept as a plain string so the rendered page is a single self-contained file
  * you can mail to someone or open from disk with no server.
  */
object Styles:
  val css: String =
    """
:root {
  --bg: #ffffff; --fg: #1b1b1f; --muted: #6b6b76; --line: #e2e2e8;
  --screen: #f4f4f6; --screen-line: #c9c9d2;
  --command: #cfe4ff; --command-line: #7aa9e0;
  --event: #ffdcae; --event-line: #e0a45c;
  --readmodel: #cdeccd; --readmodel-line: #77b877;
  --external: #e6d6f5; --external-line: #ad86cf;
  --warn-bg: #fff2f0; --warn-line: #e0897c;
  --lane-tint: #f7f7fa; --edge: #9a9aa6; --edge-far: #6b6b76;
  --note: 0 1px 2px rgba(20,20,30,.16), 0 3px 8px -4px rgba(20,20,30,.22);
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #16161a; --fg: #e8e8ee; --muted: #9a9aa6; --line: #2e2e36;
    --screen: #2a2a31; --screen-line: #45454f;
    --command: #1f3350; --command-line: #4d7db3;
    --event: #45351f; --event-line: #b3823f;
    --readmodel: #24401f; --readmodel-line: #5c8f57;
    --external: #351f45; --external-line: #8560a5;
    --warn-bg: #3a1f1b; --warn-line: #a35a4d;
    --lane-tint: #1c1c21; --edge: #6f6f7c; --edge-far: #9a9aa6;
    /* Shadows read as depth on a light wall and as nothing on a dark one, so
       dark mode leans harder on them. */
    --note: 0 1px 2px rgba(0,0,0,.5), 0 4px 10px -4px rgba(0,0,0,.6);
  }
}
* { box-sizing: border-box; }
body {
  margin: 0; padding: 2rem 1.5rem 4rem;
  background: var(--bg); color: var(--fg);
  font: 15px/1.5 ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif;
}
h1 { font-size: 1.5rem; margin: 0 0 .25rem; }
.subtitle { color: var(--muted); margin: 0 0 1.5rem; max-width: 60ch; }

.toolbar { display: flex; gap: .75rem; align-items: center; margin-bottom: 1rem; flex-wrap: wrap; }
.toolbar input {
  padding: .4rem .6rem; border: 1px solid var(--line); border-radius: 6px;
  background: var(--bg); color: var(--fg); min-width: 16rem; font: inherit;
}
.toolbar button {
  padding: .4rem .7rem; border: 1px solid var(--line); border-radius: 6px;
  background: var(--bg); color: var(--fg); cursor: pointer; font: inherit;
}
.toolbar button:hover { border-color: var(--muted); }

.legend { display: flex; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.25rem; color: var(--muted); font-size: .85rem; }
.legend span { display: inline-flex; align-items: center; gap: .4rem; }
.swatch { width: .85rem; height: .85rem; border-radius: 3px; display: inline-block; box-shadow: var(--note); }
.swatch.screen { background: var(--screen); }
.swatch.command { background: var(--command); }
.swatch.event { background: var(--event); }
.swatch.readmodel { background: var(--readmodel); }
.swatch.external { background: var(--external); }

.violations {
  border: 1px solid var(--warn-line); background: var(--warn-bg);
  border-radius: 8px; padding: .85rem 1rem; margin-bottom: 1.5rem;
}
.violations h2 { font-size: .95rem; margin: 0 0 .5rem; }
.violations ul { margin: 0; padding-left: 1.1rem; }
.violations li { margin: .2rem 0; }
.violations code { font-size: .85em; color: var(--muted); }
.ok { color: var(--muted); font-size: .9rem; margin-bottom: 1.5rem; }

/* A wall of notes rather than a table: no cell borders and no vertical rules,
   so the colours and the arrows carry the structure. */
.scroller { overflow-x: auto; padding-bottom: .5rem; }
.model {
  display: grid; row-gap: .5rem; min-width: min-content;
  position: relative; padding: .25rem 1rem 1rem 0;
  /* The column gap is a routing channel, not decoration: an arrow climbing
     several lanes in one column runs up it to get past the cards in between. */
  column-gap: .75rem;
}
.model > * { padding: .35rem .5rem; }
.corner { position: sticky; left: 0; z-index: 3; background: var(--bg); }
.lane-label {
  position: sticky; left: 0; z-index: 3; background: var(--bg);
  font-size: .75rem; text-transform: uppercase; letter-spacing: .04em;
  color: var(--muted); display: flex; align-items: center;
}
.lane-label a { color: inherit; text-decoration: none; }
.lane-label a:hover { color: var(--fg); text-decoration: underline; }
.lane-label.stream::before {
  content: ""; width: .5rem; height: .5rem; border-radius: 50%;
  background: var(--event-line); margin-right: .4rem; flex: none;
}
.lane-label.orphan::before { background: var(--warn-line); }

/* Grid rows cannot be painted directly, so a stream row gets a strip behind
   its cells. This is the only lane banding -- everything else is whitespace. */
.lane-bg {
  background: var(--lane-tint); border-radius: 8px;
  position: relative; z-index: 0; padding: 0 !important;
}

.slice-head { border-bottom: 1px solid var(--line); padding-bottom: .5rem !important; }
.slice-name { font-weight: 600; }
.badge {
  display: inline-block; margin-top: .25rem; font-size: .7rem;
  padding: .1rem .4rem; border-radius: 999px;
  border: 1px solid var(--line); color: var(--muted);
}
.hint { color: var(--muted); font-size: .8rem; }
.cell {
  display: flex; flex-direction: column; gap: .5rem; min-width: 15rem;
  position: relative; z-index: 2;
}

.card {
  border-radius: 4px; padding: .45rem .55rem; border: none;
  font-size: .85rem; box-shadow: var(--note);
}
.card-title { font-weight: 600; }
/* Inherits the note's colour: a link out of a sticky should read as the title
   still, not as a blue link pasted onto it. */
.card-link { color: inherit; text-decoration: none; }
.card-link:hover { text-decoration: underline; }
.card-link::after { content: " ↗"; font-size: .75em; color: var(--muted); }
.card.screen { background: var(--screen); }
.card.command { background: var(--command); }
.card.event { background: var(--event); }
.card.readmodel { background: var(--readmodel); }
.card.external { background: var(--external); }
/* A processor is a step, not a stored thing -- dashed says so without the
   solid fill claiming it holds data. */
.card.processor { background: var(--screen); outline: 1px dashed var(--screen-line); outline-offset: -1px; }
.card .desc { color: var(--muted); font-size: .8rem; margin-top: .15rem; }
.stream { font-size: .7rem; color: var(--muted); margin-top: .2rem; }

.fields { list-style: none; margin: .35rem 0 0; padding: 0; }
.fields li { display: flex; justify-content: space-between; gap: .5rem; font-size: .78rem; }
.fields .ftype { color: var(--muted); }
body.hide-fields .fields { display: none; }

.sitenav {
  display: flex; gap: .6rem; align-items: center; flex-wrap: wrap;
  padding-bottom: .75rem; margin-bottom: 1.5rem;
  border-bottom: 1px solid var(--line); font-size: .85rem;
}
.sitenav a { color: var(--muted); text-decoration: none; }
.sitenav a:hover { color: var(--fg); text-decoration: underline; }
.sitenav a.home { color: var(--fg); font-weight: 600; }
.sitenav .sep { color: var(--line); }

.cta { font-weight: 600; }

.board { display: flex; flex-wrap: wrap; gap: 1rem; margin-top: 1.5rem; }
.model-card {
  border: 1px solid var(--line); border-radius: 8px; padding: .9rem 1.1rem;
  flex: 1 1 20rem; min-width: 18rem;
}
.model-card.flagged { border-color: var(--warn-line); }
.model-card h2 { font-size: 1.05rem; margin: 0 0 .3rem; }
.model-card h2 a { color: var(--fg); text-decoration: none; }
.model-card h2 a:hover { text-decoration: underline; }
.facts { list-style: none; display: flex; flex-wrap: wrap; gap: .75rem; margin: .5rem 0; padding: 0; font-size: .8rem; color: var(--muted); }
.facts .warn-inline { color: var(--warn-line); font-weight: 600; }
.facts .ok-inline { color: var(--muted); }

/* A branch leaves the timeline, so it is deliberately not a sticky note: dashed
   and unfilled, so it reads as a way off the board rather than a step on it. */
.altflow {
  display: inline-flex; align-items: baseline; gap: .35rem; flex-wrap: wrap;
  font-size: .78rem; color: var(--muted); text-decoration: none;
  border: 1px dashed var(--muted); border-radius: 6px; padding: .3rem .5rem;
}
.altflow:hover { color: var(--fg); border-color: var(--fg); background: var(--lane-tint); }
.altflow-icon { font-size: .95rem; }
.altflow-reason { color: var(--fg); }
.altflow-model { font-size: .72rem; }
.altflow-model::before { content: "→ "; }
.lane-label.branches { font-style: italic; }

.origin { list-style: none; margin: .5rem 0 0; padding: 0; max-width: 34rem; }
.origin li {
  display: flex; justify-content: space-between; gap: 1rem;
  padding: .25rem 0; border-bottom: 1px solid var(--line); font-size: .85rem;
}
.origin li:last-child { border-bottom: none; }
.glossary { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: 1.5rem; }
.glossary-entry {
  border: 1px solid var(--line); border-radius: 8px; padding: .6rem .8rem;
  flex: 1 1 16rem; min-width: 14rem;
}
.glossary-entry.flagged { border-color: var(--warn-line); }
.glossary-entry h3 { font-size: .9rem; margin: 0 0 .3rem; display: flex; align-items: center; gap: .35rem; flex-wrap: wrap; }
.glossary-entry .subtitle { margin: .15rem 0; font-size: .78rem; }
.usedby { list-style: none; margin: .4rem 0 0; padding: 0; }
.usedby li { display: flex; gap: .5rem; font-size: .78rem; padding: .1rem 0; }
.usedby .kind { color: var(--muted); min-width: 6.5rem; }

.capture { margin-bottom: 1.1rem; }
.collect { color: var(--fg); font-style: italic; }
.subtitle.already { margin: .3rem 0 0; font-size: .8rem; }
.capture h3 { font-size: .9rem; margin: 0 0 .2rem; }
.capture h3 a { color: var(--fg); }
.capture .subtitle { display: inline; margin: 0; font-weight: 400; }
.inline-tag {
  display: inline-block; border: 1px solid var(--line); border-radius: 999px;
  padding: 0 .45rem; margin-right: .3rem; font-size: .78rem; color: var(--fg);
}
.outline { margin-top: 2rem; }
.outline h2 { font-size: 1rem; margin: 0 0 .5rem; }
.chapter { margin-bottom: 1.25rem; }
.chapter h3 { font-size: .95rem; margin: 0 0 .35rem; }
.subchapter { margin: .5rem 0 .5rem 1rem; }
.subchapter h4 {
  font-size: .75rem; text-transform: uppercase; letter-spacing: .04em;
  color: var(--muted); margin: .5rem 0 .25rem;
}
.slice-list { list-style: none; margin: 0; padding: 0; }
.slice-list li { display: flex; align-items: center; gap: .5rem; padding: .18rem 0; }
.slice-list a { color: var(--fg); }

.chapter-band, .sub-band {
  font-size: .78rem; font-weight: 600; text-align: center;
  border-bottom: 1px solid var(--line);
}
.chapter-band { background: var(--screen) !important; }
.sub-band { color: var(--muted); font-weight: 500; }
.chapter-band.empty, .sub-band.empty { border-bottom-color: transparent; }
.slice-name { color: var(--fg); font-weight: 600; }

.slice-detail { display: flex; flex-wrap: wrap; gap: 1.25rem; margin: 1.5rem 0; }
.group { min-width: 15rem; flex: 1 1 15rem; }
.group h2 {
  font-size: .72rem; text-transform: uppercase; letter-spacing: .04em;
  color: var(--muted); margin: 0 0 .4rem;
}

.narrative { display: flex; gap: .75rem; align-items: flex-start; min-width: min-content; }
.narrative-item { min-width: 13rem; }
.fnote { display: block; width: 100%; color: var(--muted); font-style: italic; font-size: .72rem; }
.stream.warn { color: var(--warn-line); font-weight: 600; }

/* Anything validation implicated. The point is that a gap is visible on the
   model itself, not only in the report above it. Now that cards have no border,
   the red ring is a shadow -- which also keeps red as the only outline on the
   board, so it still reads as an exception. */
.card.flagged { box-shadow: 0 0 0 2px var(--warn-line), var(--note); }
.fields li.flagged { color: var(--warn-line); font-weight: 600; }
.fields li.flagged .ftype { color: var(--warn-line); opacity: .8; }
.warn-icon { margin-right: .2rem; cursor: help; }

/* Following a violation link flashes its card, so the eye lands on the right
   sticky note instead of hunting the grid. */
:target { scroll-margin: 6rem; }
.card:target {
  animation: flash 2s ease-out 1;
  border-color: var(--warn-line);
}
@keyframes flash {
  0%, 15%   { box-shadow: 0 0 0 4px var(--warn-line); }
  30%       { box-shadow: 0 0 0 0 var(--warn-line); }
  45%       { box-shadow: 0 0 0 4px var(--warn-line); }
  100%      { box-shadow: 0 0 0 0 transparent; }
}
@media (prefers-reduced-motion: reduce) {
  .card:target { animation: none; box-shadow: 0 0 0 3px var(--warn-line); }
}
.violation-link { color: inherit; text-decoration: none; }
.violation-link:hover { text-decoration: underline; }
.violations li { cursor: pointer; }
.rule-tag {
  font-size: .68rem; text-transform: uppercase; letter-spacing: .03em;
  border: 1px solid var(--warn-line); border-radius: 999px;
  padding: 0 .35rem; color: var(--warn-line);
}

/* The arrows. Drawn into an SVG layer over the grid, sized to it, so they
   travel with horizontal scroll and never intercept a click. Without JS there
   are simply no arrows and the board still reads. */
.edges {
  position: absolute; inset: 0; pointer-events: none; z-index: 1;
  overflow: visible; padding: 0;
}
.edge { fill: none; stroke: var(--edge); stroke-width: 1.5; }
.arrowhead { fill: var(--edge); }
.arrowhead.far { fill: var(--edge-far); }
/* Long arrows reaching back along the timeline are the worst of the clutter in
   a printed model, so they wait until you ask for them. */
.edge.far { stroke: var(--edge-far); stroke-width: 1.75; stroke-dasharray: 4 3; opacity: 0; }
/* Always drawn: the branch marker sits several rows below the slice it leaves,
   and without the line it is not obvious which slice it belongs to. */
.edge.branch { stroke-dasharray: 2 4; opacity: .8; }
.model.focusing .edge.far.lit { opacity: 1; }
.model.focusing .edge:not(.far) { opacity: .25; }
.model.focusing .edge:not(.far).lit { opacity: 1; }
.edge, .cell { transition: opacity .15s ease; }
.model.focusing .cell:not(.lit) { opacity: .3; }
@media (prefers-reduced-motion: reduce) {
  .edge, .cell { transition: none; }
}

.slice-head { cursor: pointer; }
.slice-head.pinned { border-bottom-color: var(--fg); }

.rules { margin-top: 2rem; }
.rules h2 { font-size: 1rem; margin: 0 0 .75rem; }
details.rule { border: 1px solid var(--line); border-radius: 8px; padding: .5rem .75rem; margin-bottom: .5rem; }
details.rule summary { cursor: pointer; font-weight: 600; font-size: .9rem; }
details.rule .slice-of { color: var(--muted); font-weight: 400; }
.gwt { margin: .5rem 0 0; padding-left: 1rem; font-size: .87rem; }
.gwt dt { font-weight: 600; font-size: .72rem; text-transform: uppercase; color: var(--muted); margin-top: .4rem; }
.gwt dd { margin: .1rem 0 0; }
.rejected { color: var(--warn-line); }
[hidden] { display: none !important; }
"""
