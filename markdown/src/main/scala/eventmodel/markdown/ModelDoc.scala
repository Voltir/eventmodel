package eventmodel.markdown

import eventmodel.*
import eventmodel.render.Paths

/** One model as a plain-text document.
  *
  * Written to be pasted into a prompt: no decoration, stable ordering, and
  * every fact the HTML site shows. Everything structural comes from
  * [[SliceView]], the same source the storyboard's arrows come from, so the two
  * backends cannot describe the model differently.
  */
object ModelDoc:

  /** `linkTo` differs between the per-model files, where another model is a
    * sibling file, and the board file, where it is a heading in the same
    * document.
    */
  def render(model: EventModel, violations: List[Violation], linkTo: EventModel => String): String =
    val parts = List(
      heading(model),
      chapters(model, linkTo),
      streams(model),
      screens(model),
      glossary(model),
      unresolved(violations)
    )
    parts.filter(_.nonEmpty).mkString("\n\n")

  private def heading(model: EventModel): String =
    val title = s"# ${model.name}"
    if model.description.isEmpty then title else s"$title\n\n${model.description}"

  // --- slices ---------------------------------------------------------------

  private def chapters(model: EventModel, linkTo: EventModel => String): String =
    val byName = model.views.map(v => v.name -> v).toMap
    model.chapters
      .map { chapter =>
        val head =
          if chapter.description.isEmpty then s"## ${chapter.name}"
          else s"## ${chapter.name}\n\n${chapter.description}"

        val body = chapter.subChapters.map { sub =>
          // An unnamed sub-chapter contributes no heading, so its slices move up
          // a level rather than leaving a gap in the outline.
          val subHead = if sub.name.isEmpty then "" else s"### ${sub.name}\n\n"
          val level   = if sub.name.isEmpty then "###" else "####"
          subHead + sub.slices
            .flatMap(s => byName.get(s.name))
            .map(slice(_, level, linkTo))
            .mkString("\n\n")
        }

        (head +: body).mkString("\n\n")
      }
      .mkString("\n\n")

  private def slice(view: SliceView, level: String, linkTo: EventModel => String): String =
    val blocks = List(
      s"$level ${view.name} — ${view.pattern}",
      flowLine(view),
      // Only what this slice emits. A State View's events belong to the slices
      // that produced them and are written out in full there; repeating them
      // here would say the same thing twice, which is the same reason the
      // storyboard draws an arrow back rather than a second sticky note.
      view.emitted.filter(_.fields.nonEmpty).map(element).mkString("\n\n"),
      rules(view.slice),
      branches(view, linkTo)
    )
    blocks.filter(_.nonEmpty).mkString("\n\n")

  /** The slice's wiring on one line, elements grouped by how far along they sit.
    *
    * Layering the flow rather than listing each edge keeps a State View reading
    * as "these events feed this read model" instead of repeating the read model
    * once per event.
    */
  private def flowLine(view: SliceView): String =
    if view.flow.isEmpty then ""
    else
      val incoming = view.flow.groupMap(_._2.name)(_._1.name)

      def depth(name: String, seen: Set[String]): Int =
        incoming.get(name) match
          case None => 0
          case Some(sources) =>
            // `seen` guards a model that wires a cycle inside one slice, which
            // is not meaningful but should not hang the renderer.
            val usable = sources.filterNot(seen.contains)
            if usable.isEmpty then 0 else usable.map(s => depth(s, seen + name)).max + 1

      val layers = view.nodes.distinctBy(_.name).groupBy(n => depth(n.name, Set(n.name)))

      layers.toList
        .sortBy(_._1)
        .map { (_, nodes) =>
          val kinds = nodes.map(_.label).distinct
          if kinds.sizeIs == 1 then
            val plural = if nodes.sizeIs > 1 then s"${kinds.head}s" else kinds.head
            s"${nodes.map(_.name).mkString(", ")} ($plural)"
          else nodes.map(n => s"${n.name} (${n.label})").mkString(", ")
        }
        .mkString(" → ")

  private def element(n: Node): String =
    val detail = if n.detail.isEmpty then "" else s" · ${n.detail}"
    s"**${n.name}** — ${n.label}$detail\n\n${fields(n.fields)}"

  private def fields(fs: List[Field]): String =
    fs.map { f =>
      val notes = List(
        if f.isDerived then s"derived from ${f.derivedFrom.mkString(", ")}" else "",
        f.note
      ).filter(_.nonEmpty).mkString("; ")
      if notes.isEmpty then s"- `${f.name}`: ${f.tpe}"
      else s"- `${f.name}`: ${f.tpe} — $notes"
    }.mkString("\n")

  private def rules(slice: Slice): String =
    val gwts = slice match
      case sc: Slice.StateChange => sc.rules.map(gwt)
      case a: Slice.Automation   => a.rules.map(gwt)
      case t: Slice.Translation  => t.rules.map(gwt)
      case sv: Slice.StateView   => sv.rules.map(gt)
    if gwts.isEmpty then "" else s"Rules:\n\n${gwts.mkString("\n\n")}"

  private def given_(events: List[Event]): String =
    if events.isEmpty then "  - Given: nothing yet"
    else s"  - Given: ${events.map(_.name).mkString(", ")}"

  private def gwt(r: Gwt): String =
    val outcome = r.thenOutcome match
      case Outcome.Events(es)    => es.map(_.name).mkString(", ")
      case Outcome.Rejected(why) => s"rejected — $why"
    s"""- **${r.name}**
       |${given_(r.givenEvents)}
       |  - When: ${r.whenCommand.name}
       |  - Then: $outcome""".stripMargin

  private def gt(r: Gt): String =
    s"""- **${r.name}**
       |${given_(r.givenEvents)}
       |  - Then: ${r.thenExpectation}""".stripMargin

  private def branches(view: SliceView, linkTo: EventModel => String): String =
    if view.altFlows.isEmpty then ""
    else
      val items = view.altFlows.map(a => s"- ${a.reason} → [${a.model.name}](${linkTo(a.model)})")
      s"Branches:\n\n${items.mkString("\n")}"

  // --- the rest of the model ------------------------------------------------

  private def streams(model: EventModel): String =
    if model.swimlanes.isEmpty then ""
    else
      val lanes = model.swimlanes.map { lane =>
        val desc   = if lane.description.isEmpty then "" else s"${lane.description}\n\n"
        val events = lane.events.map(e => s"- ${e.name}").mkString("\n")
        s"### ${lane.name}\n\n$desc$events"
      }
      s"## Streams\n\n${lanes.mkString("\n\n")}"

  private def screens(model: EventModel): String =
    if model.screens.isEmpty then ""
    else
      val each = model.screens.map { screen =>
        val usage = model.usageOf(screen)
        val desc  = if screen.description.isEmpty then "" else s"${screen.description}\n\n"
        // States what the screen has and needs, and nothing about why -- a
        // screen with no State View behind it is already reported under
        // Unresolved, and saying it twice just costs the reader.
        val shows =
          if usage.available.isEmpty then "- displays: none"
          else
            s"- displays (from ${usage.sources.map(_.name).mkString(", ")}): " +
              usage.available.map(_.name).mkString(", ")
        val collects = usage.commands.map { c =>
          val need = if c.mustCollect.isEmpty then "none" else c.mustCollect.map(_.name).mkString(", ")
          s"- sends `${c.command.name}`, captures: $need"
        }
        s"### ${screen.name}\n\n$desc${(shows +: collects).mkString("\n")}"
      }
      s"## Screens\n\n${each.mkString("\n\n")}"

  private def glossary(model: EventModel): String =
    val entries = model.glossary
    if entries.isEmpty then ""
    else
      val rows = entries.map { use =>
        val types = use.definitions
          .map(d => if d.isDerived then s"${d.tpe} (derived from ${d.derivedFrom.mkString(", ")})" else d.tpe)
          .distinct
          .mkString(", ")
        val where = use.usedBy.map((kind, element) => s"$kind $element").mkString(", ")
        val flag  = if use.isConflicted then " ⚠" else ""
        s"| `${use.name}`$flag | $types | $where |"
      }
      "## Glossary\n\n| attribute | type | used by |\n| --- | --- | --- |\n" + rows.mkString("\n")

  private def unresolved(violations: List[Violation]): String =
    if violations.isEmpty then "## Unresolved\n\nNone."
    else
      val items = violations.map(v => s"- **${v.rule.label}** — ${v.slice}: ${v.detail}")
      s"## Unresolved\n\n${items.mkString("\n")}"

  /** Shared with the board file so its intra-document links match its headings. */
  def anchorOf(model: EventModel): String = Paths.slug(model.name)
