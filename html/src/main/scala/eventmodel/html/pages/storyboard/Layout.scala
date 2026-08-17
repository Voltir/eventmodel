package eventmodel.html
package pages
package storyboard

import eventmodel.*
import scalatags.Text.all.*

/** Where each element is drawn.
  *
  * The board draws every element exactly once. The book never redraws a sticky
  * note -- a State View reaching back to an earlier event gets an arrow, not a
  * copy -- so an element has one home column and everything else points at it.
  */
object Layout:

  /** A home, as a 0-based slice index and a column count. Only screens ever
    * span more than one column.
    */
  type Placement = Map[String, (Int, Int)]

  /** One drawn cell. `span` is above 1 only for a shared screen. */
  final case class Cell(col: Int, span: Int, slices: List[String], content: Frag)

  final case class Lane(label: String, href: Option[String], kind: String, cells: List[Cell])

  private def firstWins(pairs: List[(String, Int)]): Map[String, Int] =
    pairs.groupMapReduce(_._1)(_._2)((first, _) => first)

  /** Consecutive slices sharing a screen become one card.
    *
    * Runs do not merge across a gap: a screen returned to after an interruption
    * is a genuinely later moment on the timeline, and drawing it once would
    * flatten that.
    */
  def screenRuns(views: List[SliceView]): List[(Screen, Int, List[SliceView])] =
    views.zipWithIndex.foldRight(List.empty[(Screen, Int, List[SliceView])]) {
      case ((v, i), acc) =>
        (v.screen, acc) match
          case (Some(sc), (head, at, run) :: rest) if head == sc && at == i + 1 =>
            (sc, i, v :: run) :: rest
          case (Some(sc), rest) => (sc, i, List(v)) :: rest
          case (None, rest)     => rest
    }

  def placement(views: List[SliceView]): Placement =
    val emitted = firstWins(
      for (v, i) <- views.zipWithIndex; n <- v.emitted yield n.name -> i
    )
    // An event nothing emits still has to be drawn somewhere. Falling back to
    // the first slice that mentions it keeps it on the board, where the
    // validator's complaint about it can be seen.
    val mentioned = firstWins(
      for (v, i) <- views.zipWithIndex; e <- v.slice.allEvents yield e.name -> i
    )
    val screens = screenRuns(views).map((sc, col, run) => sc.name -> (col, run.size))

    (mentioned ++ emitted).view.mapValues((_, 1)).toMap ++ screens

  // --- lanes ----------------------------------------------------------------

  def lanesOf(model: EventModel, at: Placement)(using RenderCtx): List[Lane] =
    val views = model.views

    /** Draws an element only in its home column, so the second slice to use a
      * read model links back to the first instead of copying it.
      */
    def atHome(kind: String): List[Cell] =
      for
        (v, i) <- views.zipWithIndex
        node   <- v.nodes
        if node.kind == kind && at.get(node.name).map(_._1).contains(i)
      yield Cell(i, 1, List(v.name), Components.nodeCard(node))

    val screens = screenRuns(views).map { (sc, col, run) =>
      Cell(col, run.size, run.map(_.name), Components.screenCard(sc, s"screen/${Site.slug(sc.name)}.html"))
    }

    // Each event once, in the column of the slice that emits it.
    val events: List[(Int, String, Event, Option[Swimlane])] =
      views.zipWithIndex
        .flatMap((v, i) =>
          v.nodes.collect {
            case Node.EventNode(e, lane) if at.get(e.name).map(_._1).contains(i) => (i, v.name, e, lane)
          }
        )
        .distinctBy(_._3.name)

    def eventCells(items: List[(Int, String, Event, Option[Swimlane])]): List[Cell] =
      items
        .groupBy(_._1)
        .toList
        .sortBy(_._1)
        .map { (col, group) =>
          Cell(
            col,
            1,
            group.map(_._2).distinct,
            frag(group.map((_, _, e, lane) => Components.eventCard(e, lane, showStream = false)))
          )
        }

    // One row per swimlane: a swimlane is a stream boundary, and this is the
    // view that makes the boundary visible while reading the timeline.
    val streams = model.swimlanes.map { lane =>
      Lane(
        lane.name,
        Some(s"stream/${Site.slug(lane.name)}.html"),
        "stream",
        eventCells(events.filter((_, _, _, l) => l.contains(lane)))
      )
    }

    val orphans =
      Lane("no stream", None, "stream orphan", eventCells(events.filter((_, _, _, l) => l.isEmpty)))

    // The book puts a marker under the slice a branch leaves from. A whole lane
    // for it is only worth the space when the model actually has branches, and
    // it collapses away when it does not.
    val branches = Lane(
      "Branches",
      None,
      "branches",
      views.zipWithIndex.collect {
        case (v, i) if v.altFlows.nonEmpty =>
          Cell(i, 1, List(v.name), frag(v.altFlows.zipWithIndex.map(Edges.marker(v, _, _))))
      }
    )

    val lanes =
      List(
        Lane("Screens", None, "screens", screens),
        Lane("External", None, "external", atHome("external")),
        Lane("Commands", None, "commands", atHome("command"))
      ) ++ streams ++ List(
        orphans,
        Lane("Read Models", None, "readmodels", atHome("readmodel")),
        Lane("Automation", None, "automation", atHome("processor")),
        branches
      )

    lanes.filter(_.cells.nonEmpty)
