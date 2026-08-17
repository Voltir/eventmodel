package eventmodel.html
package pages

import eventmodel.*
import scalatags.Text.all.*

/** The board: every model on it, and how healthy each one is.
  *
  * Many small models beat one large one, so this is the page you actually
  * start from once a system is bigger than a single timeline.
  */
object BoardIndex:

  def page(board: Board, models: List[EventModel]): Page =
    val boardIssues = Validation.checkBoard(board)
    Page(
      "index.html",
      "Board",
      frag(
        h1(board.name),
        if board.description.isEmpty then frag()
        else p(cls := "subtitle", board.description),
        Components.violationsPanel(boardIssues, "this board"),
        div(cls := "board", models.map(modelCard(board, _)))
      )
    )

  private def modelCard(board: Board, model: EventModel) =
    val dir = Site.dirOf(model)
    val issues = Validation.check(model)
    val branchedFrom = board.allModels.filter(_.altFlows.exists(_.model.name == model.name))

    div(
      cls := s"model-card${if issues.isEmpty then "" else " flagged"}",
      h2(a(href := s"$dir/index.html", model.name)),
      if model.description.isEmpty then frag()
      else p(cls := "subtitle", model.description),
      ul(
        cls := "facts",
        li(s"${model.slices.size} slices"),
        li(s"${model.chapters.size} chapters"),
        li(s"${model.swimlanes.size} streams"),
        if issues.isEmpty then li(cls := "ok-inline", "no issues")
        else li(cls := "warn-inline", s"⚠ ${issues.size} unresolved")
      ),
      // An error flow is easier to place when you can see what it branches from.
      if branchedFrom.isEmpty then frag()
      else
        p(
          cls := "subtitle",
          "alternative flow from ",
          branchedFrom.map(m => a(href := s"${Site.dirOf(m)}/storyboard.html", m.name))
        ),
      p(a(cls := "cta", href := s"$dir/storyboard.html", "Storyboard →"))
    )
