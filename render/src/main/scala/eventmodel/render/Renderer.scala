package eventmodel.render

import eventmodel.*
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** One file to write. Content is already rendered -- a backend decides its own
  * markup, and everything below this point just moves strings to disk.
  */
final case class OutputFile(path: String, content: String)

/** An output format.
  *
  * A board is written by calling each backend you want:
  *
  * {{{
  * Html.render(board, Path.of("out"))
  * Markdown.render(board, Path.of("out"))
  * }}}
  */
trait Renderer:

  /** Names this format in the console summary. */
  def name: String

  /** File extensions this backend owns, without the dot.
    *
    * Backends share an output directory, so cleaning up cannot mean wiping it:
    * the second render of a board would delete the first's work. Instead each
    * backend removes only stale files it could have written itself.
    */
  def owns: Set[String]

  /** Every file this board becomes, in write order. */
  def files(board: Board): List[OutputFile]

  /** Writes the board into its own directory under `outRoot`, named after the
    * board, and prints a summary.
    *
    * Deriving the directory means two boards cannot quietly overwrite each
    * other, and there is one less name to keep in sync. Use [[write]] to choose
    * the directory yourself.
    */
  final def render(board: Board, outRoot: Path): Unit =
    println(write(board, outRoot.resolve(Paths.slug(board.name))).report)

  /** Writes the board, replacing anything this backend previously left there.
    *
    * Renaming a model or a slice changes its path, and a stale file left behind
    * still looks valid and still gets served.
    */
  final def write(board: Board, outDir: Path): Renderer.Result =
    val out = files(board)
    clean(outDir)

    out.foreach { file =>
      val target = outDir.resolve(file.path)
      Files.createDirectories(target.getParent)
      Files.writeString(target, file.content)
    }

    Renderer.Result(
      name,
      outDir,
      out.map(_.path),
      Validation.checkBoard(board).map(board.name -> _) ++
        board.allModels.flatMap(m => Validation.check(m).map(m.name -> _))
    )

  private def clean(outDir: Path): Unit =
    if !Files.exists(outDir) then return

    val stale = Files.walk(outDir).iterator.asScala.filter { p =>
      Files.isRegularFile(p) && owns.contains(extensionOf(p))
    }.toList
    stale.foreach(Files.delete)

    // Deepest first, so a directory emptied by the pass above is itself removed.
    Files
      .walk(outDir)
      .sorted(java.util.Comparator.reverseOrder())
      .iterator
      .asScala
      .filter(p => p != outDir && Files.isDirectory(p))
      .foreach { p =>
        val empty = Files.list(p).iterator.asScala.isEmpty
        if empty then Files.delete(p)
      }

  private def extensionOf(p: Path): String =
    val n = p.getFileName.toString
    val i = n.lastIndexOf('.')
    if i < 0 then "" else n.substring(i + 1)

object Renderer:

  final case class Result(
      format: String,
      outDir: Path,
      files: List[String],
      violations: List[(String, Violation)]
  ):
    def report: String =
      val header = s"$format: wrote ${files.size} files to ${outDir.toAbsolutePath}"
      if violations.isEmpty then s"$header\nvalidation: ok"
      else
        val lines = violations.map { (scope, v) =>
          s"  - $scope [${v.rule.label}] ${v.slice}: ${v.detail}"
        }
        (s"$header\nvalidation: ${violations.size} unresolved" +: lines).mkString("\n")
