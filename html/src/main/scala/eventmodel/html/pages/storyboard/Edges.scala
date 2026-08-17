package eventmodel.html
package pages
package storyboard

import eventmodel.*
import scalatags.Text.all.*

/** The connectors between notes.
  *
  * Every edge comes from `SliceView.flow`, so the arrows on the board and the
  * flow lines in any other backend describe the same wiring by construction.
  */
object Edges:

  /** Ids here are model-scoped: each element is drawn once, so there is nothing
    * to disambiguate.
    */
  def idOf(element: String): String = Components.elementId("", element)

  def markerId(view: SliceView, i: Int): String = s"alt-${Site.slug(view.name)}-$i"

  /** `far` means the two ends sit in different columns, which after
    * deduplication is exactly the case the book draws as a long arrow reaching
    * back along the timeline.
    */
  private final case class Edge(
      from: String,
      to: String,
      slice: String,
      far: Boolean,
      branch: Boolean = false
  )

  def json(model: EventModel, at: Layout.Placement): String =
    // Overlapping column ranges, so a wide screen card counts as near to every
    // command underneath it rather than only the first.
    def near(a: String, b: String): Boolean =
      (at.get(a), at.get(b)) match
        case (Some((ac, an)), Some((bc, bn))) => ac < bc + bn && bc < ac + an
        case _                                => false

    val flow = model.views
      .flatMap(v => v.flow.map((f, t) => Edge(idOf(f.name), idOf(t.name), Site.slug(v.name), !near(f.name, t.name))))
      .distinctBy(e => (e.from, e.to))

    // A branch leaves the timeline rather than continuing it, so it gets its
    // own arrow down to the marker instead of being left to be noticed.
    val branches = model.views.flatMap { v =>
      v.altFlows.indices.map { i =>
        Edge(idOf(v.branchFrom.name), markerId(v, i), Site.slug(v.name), far = false, branch = true)
      }
    }

    // Every value is a slug, so there is nothing here that needs escaping.
    (flow ++ branches)
      .map { e =>
        s"""{"from":"${e.from}","to":"${e.to}","slice":"${e.slice}",""" +
          s""""far":${e.far},"branch":${e.branch}}"""
      }
      .mkString("[", ",", "]")

  /** From `<dir>/storyboard.html`, another model is one level up.
    *
    * Naming the model it leads to as well as the reason: "submission fails" on
    * its own says a branch exists but not where it goes.
    */
  def marker(view: SliceView, alt: AltFlow, i: Int): Frag =
    a(
      cls := "altflow",
      id := markerId(view, i),
      href := s"../${Site.dirOf(alt.model)}/storyboard.html",
      span(cls := "altflow-icon", "⤳"),
      span(cls := "altflow-reason", alt.reason),
      span(cls := "altflow-model", alt.model.name)
    )
