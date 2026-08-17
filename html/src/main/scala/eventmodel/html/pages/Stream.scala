package eventmodel.html
package pages

import eventmodel.*
import scalatags.Text.all.*

/** One swimlane in isolation.
  *
  * This is the book's own way of validating a stream boundary: hide every lane
  * but one, read its events left to right, and see whether they form a
  * compelling narrative to someone from the business side. If they don't, the
  * boundary is wrong.
  */
object Stream:

  def page(model: EventModel, lane: Swimlane, violations: List[Violation], dir: String): Page =
    given RenderCtx = RenderCtx(s"stream-${Site.slug(lane.name)}", Flags.from(violations))
    Page(
      s"$dir/stream/${Site.slug(lane.name)}.html",
      lane.name,
      frag(
        h1(lane.name),
        if lane.description.isEmpty then frag() else p(cls := "subtitle", lane.description),
        p(
          cls := "subtitle",
          "Read left to right. These events should tell a coherent story on " +
            "their own -- if they don't, this stream boundary is in the wrong place."
        ),
        if lane.events.isEmpty then p(cls := "ok", "No events in this stream.")
        else div(cls := "scroller", div(cls := "narrative", lane.events.map(card(model, _)))),
        producedBy(model, lane)
      ),
      model = Some(model)
    )

  private def card(model: EventModel, e: Event)(using RenderCtx) =
    div(cls := "narrative-item", Components.eventCard(e, model.swimlaneOf.get(e)))

  /** Which slice puts each event into this stream -- the answer to "where does
    * this come from?" without leaving the page.
    */
  private def producedBy(model: EventModel, lane: Swimlane) =
    val rows =
      for
        e <- lane.events
        s <- model.slices.filter(_.allEvents.contains(e))
      yield li(
        span(cls := "fname", e.name),
        " ← ",
        a(href := s"../slice/${Site.slug(s.name)}.html", s.name),
        span(cls := "badge", s.pattern)
      )

    if rows.isEmpty then frag()
    else div(cls := "outline", h2("Produced by"), ul(cls := "slice-list", rows))
