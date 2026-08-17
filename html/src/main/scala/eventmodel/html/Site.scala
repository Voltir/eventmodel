package eventmodel.html

import eventmodel.*
import scalatags.Text.all.*
import scalatags.Text.tags2

/** One output file. `path` is relative to the output directory.
  *
  * `model` is empty for the board index, which belongs to no single model.
  */
final case class Page(
    path: String,
    title: String,
    content: Frag,
    model: Option[EventModel] = None
):
  /** Prefix that gets a link from this page back to the output root.
    *
    * Pages sit at different depths (`index.html` vs `cart/slice/add-item.html`)
    * and the site is opened from disk, so links have to be relative.
    */
  def up: String = "../" * path.count(_ == '/')

object Site:

  /** Not dot-prefixed: some static servers hide dotfiles. */
  val BuildStampFile = "build.txt"

  /** The id of the element a violation is about, if it names one.
    *
    * Scoped by slice to match the ids the pages emit, since the same event can
    * appear in several slices on one page.
    */
  /** `scope` is empty on pages that draw each element once -- see [[RenderCtx]]
    * -- and the slice slug on pages where the same event appears in several.
    */
  def anchorFor(scope: Violation => String)(v: Violation): Option[String] =
    v.target.map(t => Components.elementId(scope(v), t.element))

  /** Per-slice scoping, for pages that show one element more than once. */
  val bySlice: Violation => String = v => slug(v.slice)

  /** No scoping, for pages where every element appears exactly once. */
  val unscoped: Violation => String = _ => ""

  export eventmodel.render.Paths.{slug, dirOf}

  def allPages(board: Board): List[Page] =
    val models = board.allModels
    pages.BoardIndex.page(board, models) +: models.flatMap(pagesFor)

  private def pagesFor(model: EventModel): List[Page] =
    val violations = Validation.check(model)
    val dir = dirOf(model)
    List(
      pages.Index.page(model, violations, dir),
      pages.Storyboard.page(model, violations, dir),
      pages.Glossary.page(model, dir)
    ) ++ model.views.map(v => pages.SliceDetail.page(model, v, violations, dir))
      ++ model.swimlanes.map(l => pages.Stream.page(model, l, violations, dir))
      ++ model.screens.map(s => pages.ScreenPage.page(model, s, dir))

  /** Wraps a page's content in the shared chrome. Styles and script are inlined
    * so every page stands alone -- no server, no shared assets to lose.
    */
  def render(board: Board, page: Page, build: String): String =
    val heading = page.model.map(_.name).getOrElse(board.name)
    "<!DOCTYPE html>" + scalatags.Text.tags.html(
      lang := "en",
      head(
        meta(charset := "utf-8"),
        meta(name := "viewport", content := "width=device-width, initial-scale=1"),
        // Live reload: the script polls this file and reloads when the stamp
        // changes. Only over http(s) -- opened from disk it stays inert.
        meta(
          name := "em-build",
          content := build,
          attr("data-url") := s"${page.up}$BuildStampFile"
        ),
        tags2.title(s"${page.title} — $heading"),
        tags2.style(raw(Styles.css))
      ),
      body(
        nav(board, page),
        page.content,
        script(raw(Script.js))
      )
    ).render

  private def nav(board: Board, page: Page) =
    val u = page.up
    div(
      cls := "sitenav",
      a(cls := "home", href := s"${u}index.html", board.name),
      page.model.map { m =>
        val dir = dirOf(m)
        frag(
          span(cls := "sep", "/"),
          a(cls := "home", href := s"$u$dir/index.html", m.name),
          span(cls := "sep", "/"),
          a(href := s"$u$dir/storyboard.html", "Storyboard"),
          a(href := s"$u$dir/glossary.html", "Glossary"),
          m.swimlanes.map { lane =>
            a(href := s"$u$dir/stream/${slug(lane.name)}.html", lane.name)
          }
        )
      }
    )
