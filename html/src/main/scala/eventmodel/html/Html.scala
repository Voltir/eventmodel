package eventmodel.html

import eventmodel.*
import eventmodel.render.{OutputFile, Renderer}

/** The static site backend.
  *
  * {{{
  * @main def myBoard(): Unit =
  *   Html.render(MyBoard.board, Path.of("out"))
  * }}}
  */
object Html extends Renderer:

  val name = "html"

  /** `txt` is the live-reload stamp, which is this backend's file too. */
  val owns = Set("html", "txt")

  def files(board: Board): List[OutputFile] =
    val build = System.currentTimeMillis().toString
    val pages = Site.allPages(board).map { page =>
      OutputFile(page.path, Site.render(board, page, build))
    }
    // Last: the pages a reloading browser is about to fetch should all exist by
    // the time the stamp it polls for changes.
    pages :+ OutputFile(Site.BuildStampFile, build)
