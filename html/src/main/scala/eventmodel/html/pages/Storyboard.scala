package eventmodel.html
package pages

import eventmodel.*
import eventmodel.html.pages.storyboard.{Edges, Layout}
import scalatags.Text.all.*

/** The timeline: every element drawn once, left to right, under its chapter.
  *
  * Chapters and sub-chapters are bands above the lanes, which is where the book
  * puts them: the reader's eye picks up the current context while following the
  * timeline. [[Layout]] decides where things go and [[Edges]] how they connect.
  */
object Storyboard:

  def page(model: EventModel, violations: List[Violation], dir: String): Page =
    given RenderCtx = RenderCtx("", Flags.from(violations))
    val at    = Layout.placement(model.views)
    val lanes = Layout.lanesOf(model, at)

    Page(
      s"$dir/storyboard.html",
      "Storyboard",
      frag(
        h1(model.name),
        if model.description.isEmpty then frag()
        else p(cls := "subtitle", model.description),
        Components.legend,
        toolbar,
        // Elements live on this page, so link to the anchor rather than
        // sending the reader off to the slice page.
        Components.violationsPanel(violations, "this model", Site.anchorFor(Site.unscoped)(_).map("#" + _)),
        div(cls := "scroller", grid(model, lanes)),
        script(tpe := "application/json", id := "em-edges", raw(Edges.json(model, at))),
        rulesSection(model)
      ),
      model = Some(model)
    )

  private def toolbar =
    div(
      cls := "toolbar",
      input(tpe := "search", id := "filter", placeholder := "Filter slices…"),
      button(id := "toggle-fields", "Toggle fields"),
      button(id := "expand-rules", "Expand all rules"),
      span(cls := "hint", "hover a slice to trace where its data comes from")
    )

  private def grid(model: EventModel, lanes: List[Layout.Lane]) =
    val slices   = model.slices
    val chapters = model.chapters
    val subs     = chapters.flatMap(_.subChapters)

    // Explicit row/column placement throughout: the spanning bands would
    // otherwise fight with grid auto-placement.
    val chapterRow   = 1
    val subRow       = 2
    val headRow      = 3
    val laneRowStart = 4

    div(
      cls := "model",
      style := s"grid-template-columns: 9rem repeat(${slices.size}, minmax(15rem, 1fr));",
      div(cls := "lane-label", style := cell(chapterRow, 1), "Chapter"),
      bands(chapters.map(c => c.name -> c.subChapters.flatMap(_.slices).size), "chapter-band", chapterRow),
      div(cls := "lane-label", style := cell(subRow, 1), ""),
      bands(subs.map(s => s.name -> s.slices.size), "sub-band", subRow),
      div(cls := "corner", style := cell(headRow, 1), ""),
      slices.zipWithIndex.map { (s, i) =>
        div(
          cls := "slice-head",
          style := cell(headRow, i + 2),
          attr("data-slices") := s.name.toLowerCase,
          attr("data-slice-ids") := Site.slug(s.name),
          a(cls := "slice-name", href := s"slice/${Site.slug(s.name)}.html", s.name),
          div(cls := "badge", s.pattern)
        )
      },
      lanes.zipWithIndex.flatMap { (lane, idx) =>
        val row = laneRowStart + idx
        val label = lane.href match
          case Some(h) =>
            div(cls := s"lane-label ${lane.kind}", style := cell(row, 1), a(href := h, lane.label))
          case None =>
            div(cls := s"lane-label ${lane.kind}", style := cell(row, 1), lane.label)

        // Grid rows cannot be painted directly, so a stream row gets a strip
        // behind its cells.
        val tint =
          if lane.kind.startsWith("stream") then
            List(div(cls := "lane-bg", style := s"grid-row: $row; grid-column: 1 / -1;"))
          else Nil

        (tint :+ label) ++ lane.cells.map { c =>
          div(
            cls := "cell",
            style := s"grid-row: $row; grid-column: ${c.col + 2} / span ${c.span};",
            attr("data-slices") := c.slices.map(_.toLowerCase).mkString(" | "),
            attr("data-slice-ids") := c.slices.map(Site.slug).mkString(" "),
            c.content
          )
        }
      }
    )

  private def cell(row: Int, col: Int): String =
    s"grid-row: $row; grid-column: $col;"

  /** Bands start at column 2 (column 1 is the lane label) and span their
    * slices. Empty names render as a spacer so the grid stays aligned.
    */
  private def bands(items: List[(String, Int)], klass: String, row: Int): List[Frag] =
    val starts = items.scanLeft(2)((acc, it) => acc + it._2)
    starts.zip(items).map { case (start, (name, n)) =>
      div(
        cls := (if name.isEmpty then s"$klass empty" else klass),
        style := s"grid-row: $row; grid-column: $start / span ${math.max(n, 1)};",
        name
      )
    }

  private def rulesSection(model: EventModel) =
    val blocks = model.slices.flatMap(Components.rulesOf)
    if blocks.isEmpty then frag()
    else div(cls := "rules", h2("Business rules"), blocks)
