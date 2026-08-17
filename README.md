# eventmodel

A Scala 3 library for describing systems the way Martin Dilger's *Understanding
Eventsourcing* describes them -- as plain data -- plus a renderer that turns a
model into a single static HTML page you can read and explore.

The point is to keep the concepts and skip the web tooling: models are ordinary
Scala values, so they diff in git, refactor in an IDE, and can be checked by a
compiler and by rules of the method itself.

## Starting it

```bash
code ~/Projects/eventmodel
```

Then **Reopen in Container** (`Ctrl+Shift+P` → `Dev Containers: Reopen in
Container`). The toolchain lives in the container; don't run a second Scala
toolchain against this directory from the WSL host.

## Rendering

```bash
sbt render     # the example board; shorthand for models/runMain renderShoppingCart
sbt ~render    # re-renders on every save
```

Open `out/<board>/index.html`. Every page inlines its own CSS and JS, so the
site works straight off disk -- no server, no shared assets, no network.

```
out/<board>/index.html                    the board: every model, and its health
out/<board>/<model>/index.html            overview: chapters, streams, issues
out/<board>/<model>/storyboard.html       the timeline, chapters banded above
out/<board>/<model>/glossary.html         every attribute and where it is used
out/<board>/<model>/slice/<name>.html     one slice in full, with its rules
out/<board>/<model>/screen/<name>.html    what a screen must display and capture
out/<board>/<model>/stream/<name>.html    one swimlane in isolation

out/<board>/<model>.md                    one model as plain text
out/<board>/<board>.md                    every model, one paste
```

Each render replaces what that backend previously wrote — matched by file
extension, not by wiping the directory, so writing one format never deletes the
other's output. Renaming a model or a slice changes its path, and a stale page
left behind still looks valid and still gets served.

The stream pages exist because the book uses reading a lane in isolation as the
way to test whether a stream boundary is right: the events should tell a
coherent story on their own to someone who can't see the rest of the model.

### The storyboard

The timeline, drawn as sticky notes. Three things about how it is laid out:

**Every element is drawn once.** The book never redraws a note — a State View
reaching back to an earlier event gets an arrow, not a copy — so each element
has a single home column. An event lives where it is *emitted*; a State View
that reads it points back at it. A screen used by several consecutive slices is
one wide note spanning them. (Runs don't merge across a gap: a screen returned
to later is a genuinely later moment on the timeline.)

**Events sit in one row per swimlane**, because that is what a swimlane is. The
row is the stream, so the cards don't repeat its name, and the row label links
to that stream's page.

**Arrows carry the flow.** Short connectors within a slice are always drawn.
The long ones — a read model pulling from events several columns back — appear
when you hover a slice, or click its header to pin it. The book has to draw
every arrow at once because it is print; those fan-ins are the worst of the
clutter, and a browser can hold them until asked. Connectors route around
whatever is in the way rather than passing behind it.

**Branches get their own row** at the bottom, where the book puts the marker,
with a dashed arrow from the slice they leave. The marker is deliberately not a
sticky note — dashed and unfilled, because it is a way off the board rather than
a step on it. The row only appears when the model has branches.

The arrows are the one part that needs measuring a laid-out page, so they are
drawn in JavaScript. With JS off there are no arrows and the board still reads.

### Live reload

Two terminals in the container:

```bash
sbt serve      # static server on :8000, forwarded to the host
sbt ~render    # re-renders on every save
```

Open <http://localhost:8000>. Each render writes a new stamp to
`out/build.txt`; every page polls it once a second and reloads when it changes,
restoring your vertical scroll and how far you had scrolled the storyboard
sideways.

`sbt serve` is a thin wrapper around Python's static server, so this is
equivalent and starts instantly instead of waiting ~10s for sbt to boot:

```bash
python3 -m http.server 8000 --directory out
```

Live reload doesn't care which one is serving.

Two things that will otherwise waste your time:

- **Port 8000 must be forwarded.** `forwardPorts` in `devcontainer.json` covers
  it, but that only applies on container *creation* -- after a plain restart,
  add 8000 in the VS Code **Ports** panel.
- **`sbt serve` looks hung for ~10 seconds** on a cold start. That is sbt
  loading, not the server.

The poll is skipped entirely when a page is opened from disk over `file://`,
where `fetch` is blocked anyway -- so the same output works both ways, and
nothing is left running in a page you saved and mailed to someone.

## Layout

| | |
|---|---|
| `core/` | the model types, and everything derivable from them. Zero dependencies, no opinion about output |
| `render/` | what every output format shares: the `Renderer` contract, and moving files to disk |
| `html/` | the static site |
| `markdown/` | plain text, for pasting a model into an LLM prompt |
| `models/` | your boards. One file each |

`core` holds `SliceView`, which is the one place the four patterns are taken
apart. A slice becomes a uniform list of elements with the role each plays, and
both backends walk that rather than matching on the pattern themselves — so the
storyboard's arrows and the Markdown flow lines describe the same wiring by
construction, not by two people remembering to keep them in step.

`Validation` deliberately still matches on the pattern. Its four branches encode
genuinely different rules — a State Change tolerates unaccounted command fields
because a person can type them, an Automation does not — and flattening that
would hide the distinction that makes the check worth having.

## Writing a model

Copy `models/src/main/scala/ShoppingCart.scala` and give it a `@main` of its
own:

```scala
import eventmodel.*
import eventmodel.html.Html
import eventmodel.markdown.Markdown
import java.nio.file.Path

object Payments:
  val board: Board = Board("Payments", models = List(/* ... */))

// `renderX` rather than `x`: a main named `payments` would generate a class
// differing only in case from `Payments`, which collides on case-insensitive
// filesystems.
@main def renderPayments(): Unit =
  Html.render(Payments.board, Path.of("out"))
  Markdown.render(Payments.board, Path.of("out"))
```

Drop either line to skip that format. Both report the model's health, so running
both prints the validation summary twice.

Then:

```bash
sbt "models/runMain renderPayments"     # once
sbt "~models/runMain renderPayments"    # on every save
```

`render` puts each board in its own directory under `out/`, named after the
board, so boards cannot overwrite one another. Use `write` to choose the
directory yourself. Both come from the shared `Renderer` trait, so a new backend
needs only `name`, `owns` and `files`.

Models live in this build rather than a separate project on purpose: changes to
`core` or a backend are picked up immediately, with no `publishLocal` step.
Split them out later if the library settles.

### Markdown

`<model>.md` is the same content as the site, flattened for reading in one pass:
a flow line per slice (`Cart (screen) → Add Item (command) → Item Added
(event)`), the elements it emits with their fields, its rules, then streams,
screens, glossary and anything unresolved.

It follows the storyboard's rule that an element is written once. A State View
names the events it reads in its flow line but does not repeat their fields —
those belong to the slices that emit them, which is where they are spelled out.

`<board>.md` is every model in one file, for when you want to paste the lot.

## The model

The four patterns from the book, as one enum:

- **State Change** — screen → command → event. The only way to change anything.
- **State View** — events → read model → screen or process.
- **Automation** — a state view plus a state change plus a gear.
- **Translation** — an external event crossing the system boundary, in either
  of the two variations the book describes.

Business rules attach to slices as **GWT** (Given/When/Then) for state changes
and **GT** (Given/Then) for state views -- read models only ever read events
that already happened, so there is no "when". A state change can end in
`Outcome.Rejected`, since "THEN the system should raise an error" is a normal
result.

Slices are grouped into `Chapter` → `SubChapter`, which is how the book keeps a
developed model readable: "a chapter defines kind of a context for a given
slice". `EventModel.slices` flattens them back into reading order.

### Glossary

Attributes are global by name, deliberately. That is the book's own mechanism:
*"we need to define it consistently throughout the Event Model… for each
element and each attribute we ensure a clear data-path."* Tracing data across a
model works because one name means one thing everywhere, so fields are not
qualified by the element that carries them.

Nothing can enforce that, though — one name used for two meanings looks
identical to a checker. So `<model>/glossary.html` lists every attribute beside
every element carrying it, and you read the shared ones and ask whether they
really are the same thing. Getting this wrong is not cosmetic: one name for both
"how many are in the cart" and "how many to add" makes the Cart screen report
that it needs no input at all.

The half that *is* checkable — one name declared with two different types or
derivations — is a `Reference` violation.

### Derived fields

A read model field is often computed rather than copied — a sum, a count, a
latest-wins. Naming it for what it means then makes it look unsourced, so state
the derivation:

```scala
Field("quantityInCart", "Int", derivedFrom = List("quantity"))
```

The named sources still have to exist, so this records a derivation rather than
excusing one. A derivation naming something nothing provides is reported like
any other gap.

This matters more than it sounds. Give one name to two different things — the
quantity in the cart and the quantity being added — and a screen looks like it
needs no input at all. Name them apart and the sum needs `derivedFrom` to stay
honest. The checker pushes toward names that distinguish things, which is the
point.

### Screens

Each screen gets a page saying what it must **display** and what it must
**capture** -- the contract for whoever builds it. Both halves are derived, so
nothing is written down twice:

- displays: the read models of State Views that show this screen
- captures: the commands of State Changes that show this screen

Nothing decides whether a given field is "displayed" or "typed in". A form
pre-filled from a read model and then edited is legitimately both, field names
cannot settle it, and a frontend needs both lists regardless.

### Boards and alternative flows

A `Board` holds many models, because the book prefers many small ones over one
large one -- each capturing a single business context you can read left to right
without interruption.

Event Modeling follows one path along one timeline; conditions and loops are not
drawn. Error cases become their own model, linked from the slice they branch
from:

```scala
Slice.StateChange(
  name = "Submit Cart",
  command = submitCart,
  events = List(cartSubmitted),
  altFlows = List(AltFlow("submission fails", submitCartError))
)
```

A model reached only as an alternative flow does not need listing in
`Board.models` -- `Board.allModels` walks the links and finds it, deduplicating
by name and tolerating cycles, since an error flow linking back to the happy
path is reasonable to draw.

### Swimlanes own their events

A swimlane is a *stream boundary*, usually one per business capability -- not a
row in the diagram. Streams own their events rather than events naming a stream:

```scala
val cartStream = Swimlane(
  "cart",
  "The shopper's own cart",
  events = List(itemAdded, itemRemoved, cartSubmitted)
)
```

So an event belongs to exactly one lane. If what looks like the same event turns
up in a second lane, it genuinely is a different event in a different stream --
which is what the Translation pattern is for. `EventModel.swimlaneOf` is the
lookup the renderer uses.

## Validation

`Validation.check` returns everything checkable about a model:

| Rule | What it catches |
|---|---|
| `InformationCompleteness` | a read model exposing data no event carries; an event recording data its command never supplied |
| `StreamBoundary` | an event declared in more than one swimlane |
| `Reference` | an event a slice uses but no swimlane declares; two models sharing a name |
| `DataOrigin` | an automation issuing a command its read model can't fill in; a screen no State View feeds |

The first is the book's own information completeness check, and its point is to
catch false assumptions about data at modeling time rather than halfway through
implementation.

`DataOrigin` is the book's right-to-left pass -- trace an attribute back to
where it comes from -- applied where it actually finds something. Run literally
over events it would report nothing new, because `InformationCompleteness`
already demands an event's data come from its triggering command, which is
stricter than "from the command or anything earlier". The unchecked question is
one step further back: a human at a screen can type anything, but an automation
has only its read model, so any command field missing from it has no source at
all.

The same rule flags a screen that sends a command with no State View behind it.
A form cannot pre-fill an id or a price out of thin air, so a screen nothing
feeds is a gap regardless of who is looking at it. Deliberately no attempt is
made to check *which* command fields a person supplies -- see Screens above.

Use `Validation.check(model)` for one model and `Validation.checkBoard(board)`
for board-wide problems; `sbt render` reports both.

Violations print from `sbt render` and appear on the index, the storyboard, and
the relevant slice page. Each one carries a `Target`, so the report links to the
element it is about; the card is ringed in red — the only outline anywhere on
the board, so it reads as an exception — the offending field is marked with a ⚠,
and following the link flashes the card so your eye lands on the right sticky
note instead of hunting the grid.

The example model contains a real gap -- `Cart Items` exposes `productName`,
which nothing emits. The model is written with no hint that anything is wrong,
because that is the situation the check is for: a person at a whiteboard does
not know which assumption is the bad one. Matching is by field name, which is
crude, but a false positive just prompts the conversation the check exists to
start.

## Not built yet

- **Structured fields.** `tpe` is free-form, so a JSON-ish example renders fine
  today (`Field("products", """[{"productId": UUID, "price": Double}]""")`) —
  the book does the same. But the checks cannot see inside it, so a read model
  exposing `productId` looks unsourced even when an event carries it nested. A
  `nested: List[Field]` on `Field` would fix it, keeping `tpe` for the readable
  example.
- **Closing the books.** The book's approach to keeping streams short. Relevant
  once a model describes something long-lived.
- **Test generation.** GWTs are already structured data -- "a real example that
  can be translated into a unit test in the running system", as the book puts
  it -- so emitting scaffolding from them is mostly mechanical.

## MCP servers

`.mcp.json` wires up `github`, container-only by design. It needs `GITHUB_PAT`
in the host environment, passed through via `remoteEnv` so the token stays out
of git.

**Metals MCP is deliberately not wired up.** Metals can expose its own semantic
tools over HTTP, and `metals.startMcpServer` is on so it does -- but the port is
chosen at startup and changes, which makes a committed URL go stale silently.
At this size the compiler is a better oracle than semantic search anyway.

To add it when the codebase is big enough to want find-usages, read the current
URL out of `.vscode/mcp.json` (written by Metals, gitignored) and add:

```json
"metals": { "type": "http", "url": "http://localhost:<port>/mcp" }
```

Do not also run a standalone `metals-mcp` stdio server. Two Metals instances on
one workspace fight over `.metals/metals.mv.db` and tear down each other's build
connection, which presents as the editor silently losing all build targets.

## Build state

`.metals/` and `.bloop/` are Docker named volumes, not bind mounts. They contain
absolute paths only valid inside the container, and sharing them with the host
causes Metals to silently lose its build targets. The coursier/sbt/ivy caches
are volumes too, which keeps rebuilds fast.
