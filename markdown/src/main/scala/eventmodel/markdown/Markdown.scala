package eventmodel.markdown

import eventmodel.*
import eventmodel.render.{OutputFile, Paths, Renderer}

/** The plain-text backend, for pasting a model into an LLM prompt.
  *
  * {{{
  * @main def myBoard(): Unit =
  *   Markdown.render(MyBoard.board, Path.of("out"))
  * }}}
  *
  * Writes one file per model, plus one for the whole board -- small enough to
  * attach a single model when context is tight, and a single paste when it is
  * not.
  */
object Markdown extends Renderer:

  val name = "markdown"
  val owns = Set("md")

  def files(board: Board): List[OutputFile] =
    val models = board.allModels

    val perModel = models.map { model =>
      OutputFile(
        s"${Paths.dirOf(model)}.md",
        ModelDoc.render(model, Validation.check(model), m => s"${Paths.dirOf(m)}.md")
      )
    }

    perModel :+ OutputFile(s"${Paths.slug(board.name)}.md", boardDoc(board, models))

  /** Every model in one document. Links between models become anchors, since
    * the thing they point at is in the same file.
    */
  private def boardDoc(board: Board, models: List[EventModel]): String =
    val header =
      if board.description.isEmpty then s"# ${board.name}"
      else s"# ${board.name}\n\n${board.description}"

    val contents = models.map(m => s"- [${m.name}](#${ModelDoc.anchorOf(m)})").mkString("\n")

    val boardIssues = Validation.checkBoard(board) match
      case Nil => ""
      case vs  => "## Board issues\n\n" + vs.map(v => s"- **${v.rule.label}** — ${v.detail}").mkString("\n")

    val docs = models.map { model =>
      ModelDoc.render(model, Validation.check(model), m => s"#${ModelDoc.anchorOf(m)}")
    }

    (List(header, contents, boardIssues).filter(_.nonEmpty) ++ docs).mkString("\n\n---\n\n")
