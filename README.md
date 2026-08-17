# eventmodel

Write Event Models as plain Scala values, and render them to a static HTML site
and to Markdown.

The concepts come from Martin Dilger's *Understanding Eventsourcing*. The point
of this project is to keep those concepts and skip the web tooling: a model is
ordinary Scala, so it diffs in git, refactors in an IDE, and can be checked
automatically.

## What an event model is

An event model describes a system as a timeline: what a user sees, what they do,
what happened as a result, and what gets shown back to them. You read it left to
right, and it is built from four kinds of step, called slices:

- **State Change** — a screen sends a command, which records an event. The only
  way anything changes.
- **State View** — events feed a read model, which a screen or a process reads.
- **Automation** — a state view and a state change joined by a process, with no
  human in the middle.
- **Translation** — something from outside the system crossing the boundary.

Events are grouped into **swimlanes**, which are stream boundaries: an event
belongs to exactly one. Slices are grouped into **chapters** to keep a large
model readable.

## Quick start

Open the folder in VS Code and **Reopen in Container** (`Ctrl+Shift+P` →
`Dev Containers: Reopen in Container`). The toolchain lives in the container.

```bash
sbt render     # render the example board
sbt ~render    # re-render on every save
```

Then open `out/shopping/index.html`.

## A model

```scala
import eventmodel.*

val cartId    = Field("cartId", "UUID")
val productId = Field("productId", "UUID")
val quantity  = Field("quantity", "Int")

val cartScreen = Screen("Cart", "Line items, quantities, running total")
val addItem    = Command("Add Item", List(cartId, productId, quantity))
val itemAdded  = Event("Item Added", List(cartId, productId, quantity))

val addItemSlice = Slice.StateChange(
  name    = "Add Item",
  screen  = Some(cartScreen),
  command = addItem,
  events  = List(itemAdded),
  rules = List(
    Gwt(
      "an item can be added to an empty cart",
      givenEvents = Nil,
      whenCommand = addItem,
      thenOutcome = Outcome.Events(List(itemAdded))
    )
  )
)

val cart = EventModel(
  name      = "Shopping Cart",
  chapters  = List(Chapter.flat("Shopping", addItemSlice)),
  swimlanes = List(Swimlane("cart", events = List(itemAdded)))
)

val board = Board("Shopping", models = List(cart))
```

`models/src/main/scala/ShoppingCart.scala` is a fuller version using all four
patterns. Copy it to start your own.

## What you get

```
out/<board>/index.html                 every model on the board
out/<board>/<model>/storyboard.html    the timeline, as sticky notes
out/<board>/<model>/index.html         chapters, streams, open issues
out/<board>/<model>/glossary.html      every attribute and where it is used
out/<board>/<model>/slice/<name>.html  one slice, with its rules
out/<board>/<model>/screen/<name>.html what a screen shows and captures
out/<board>/<model>/stream/<name>.html one swimlane on its own

out/<board>/<model>.md                 one model as plain text
out/<board>/<board>.md                 the whole board, for pasting somewhere
```

Pages inline their own CSS and JS, so the site works straight off disk.

On the storyboard, each element is drawn once and arrows show the flow. Hovering
a slice reveals the longer arrows reaching back to earlier events; clicking its
header pins them.

## Checks

`sbt render` validates the model and prints anything unresolved. Violations also
appear in both outputs, linked to the element they are about.

| Rule | What it catches |
|---|---|
| `InformationCompleteness` | a read model exposing data no event carries; an event recording data its command never supplied |
| `StreamBoundary` | an event declared in more than one swimlane |
| `Reference` | an event no swimlane declares; two models sharing a name |
| `DataOrigin` | an automation issuing a command its read model can't fill in; a screen no State View feeds |

Matching is by field name, so give one thing one name throughout. If a read
model field is computed rather than copied, say where it comes from:

```scala
Field("quantityInCart", "Int", derivedFrom = List("quantity"))
```

The example model contains a deliberate gap, so you can see a violation
reported.

## Writing your own

Copy `ShoppingCart.scala`, and give it a `@main`:

```scala
@main def renderPayments(): Unit =
  Html.render(Payments.board, Path.of("out"))
  Markdown.render(Payments.board, Path.of("out"))
```

Name the main `renderPayments` rather than `payments` — a main matching an
object name differing only in case collides on macOS and Windows. Drop either
line to skip that format.

```bash
sbt "models/runMain renderPayments"
sbt "~models/runMain renderPayments"    # on every save
```

## Live reload

```bash
sbt serve      # static server on :8000
sbt ~render    # in another terminal
```

Open <http://localhost:8000>. Pages poll a build stamp and reload themselves,
keeping your scroll position. Opened from disk over `file://` the polling is
skipped, so the same output works both ways.

## Layout

| | |
|---|---|
| `core/` | the model types and everything derived from them. No dependencies |
| `render/` | the `Renderer` contract, shared by every output format |
| `html/` | the static site |
| `markdown/` | plain text |
| `models/` | your boards, one file each |

A new output format needs `name`, `owns` and `files`. Each format cleans up only
the file extensions it owns, so writing one never deletes another's output.

The code carries the reasoning behind these decisions — start with
`core/View.scala` and `core/Validation.scala`.
