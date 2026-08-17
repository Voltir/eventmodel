package eventmodel

/** Which rule a violation broke. */
enum Rule:
  /** The book's information completeness check: no attribute may appear
    * without a traceable source.
    */
  case InformationCompleteness

  /** Swimlanes are stream boundaries, so an event belongs to exactly one. */
  case StreamBoundary

  /** Something a slice uses was never declared, or was declared twice. */
  case Reference

  /** Data a machine-issued command needs, with no human to type it.
    *
    * This is the book's right-to-left pass -- tracing an attribute back to
    * where it comes from -- applied where it actually finds something. Run
    * literally over events it would report nothing new, because
    * [[InformationCompleteness]] already demands an event's data come from its
    * triggering command, which is stricter than "from the command or anything
    * earlier". The unchecked question is one step further back: an automation
    * has only its read model to build a command from.
    */
  case DataOrigin

object Rule:
  extension (r: Rule)
    def label: String = r match
      case InformationCompleteness => "information completeness"
      case StreamBoundary          => "stream boundary"
      case Reference               => "undeclared reference"
      case DataOrigin              => "data origin"

/** What a violation is about, so a renderer can point at it rather than only
  * describing it. `field` is empty when the whole element is at fault.
  */
final case class Target(element: String, field: String = "")

final case class Violation(
    rule: Rule,
    slice: String,
    detail: String,
    target: Option[Target] = None
)

/** Everything that can be checked about a model without running it.
  *
  * The information completeness check is the book's, and its point is to catch
  * false assumptions about data at modeling time rather than halfway through
  * implementation:
  *
  *   - a read model may only expose data some event already carries
  *   - an event may only carry data its triggering command supplies
  *
  * Matching is by field name. That is crude, but it mirrors what a person does
  * reading stickies off a wall, and a false positive is cheap -- it prompts a
  * conversation, which is the whole idea.
  */
object Validation:

  def check(model: EventModel): List[Violation] =
    val everyEvent = model.slices.flatMap(_.allEvents)
    model.slices.flatMap(completeness(model, _, everyEvent)) ++
      streamBoundaries(model) ++
      undeclaredEvents(model) ++
      conflictingAttributes(model)

  /** Board-wide checks. Per-model violations are reported by [[check]]. */
  def checkBoard(board: Board): List[Violation] =
    val byName = board.allModels.groupBy(_.name)
    for
      (name, models) <- byName.toList.sortBy(_._1)
      if models.sizeIs > 1
    yield Violation(
      Rule.Reference,
      name,
      s"$name names ${models.size} different models; names identify models on a " +
        "board and become page paths, so all but one would be dropped",
      Some(Target(name))
    )

  // --- stream rules ---------------------------------------------------------

  private def streamBoundaries(model: EventModel): List[Violation] =
    val lanesByEvent = model.swimlanes
      .flatMap(lane => lane.events.map(_ -> lane))
      .groupMap(_._1)(_._2)

    for
      (event, lanes) <- lanesByEvent.toList.sortBy(_._1.name)
      if lanes.sizeIs > 1
    yield Violation(
      Rule.StreamBoundary,
      lanes.map(_.name).mkString(", "),
      s"event '${event.name}' is declared in ${lanes.size} swimlanes " +
        s"(${lanes.map(_.name).mkString(", ")}); a swimlane is a stream boundary, " +
        "so an event belongs to exactly one",
      Some(Target(event.name))
    )

  private def undeclaredEvents(model: EventModel): List[Violation] =
    val declared = model.swimlanes.flatMap(_.events).toSet
    for
      slice <- model.slices
      event <- slice.allEvents.distinct
      if !declared.contains(event)
    yield Violation(
      Rule.Reference,
      slice.name,
      s"event '${event.name}' is used here but belongs to no swimlane",
      Some(Target(event.name))
    )

  /** One name used with two different types or derivations.
    *
    * This is the half of "define it consistently throughout the Event Model"
    * that can be checked. The other half -- one name, one type, two meanings --
    * is invisible to a checker and belongs on the glossary page.
    */
  private def conflictingAttributes(model: EventModel): List[Violation] =
    for
      use <- model.glossary
      if use.isConflicted
    yield
      val shapes = use.definitions
        .map(f => if f.isDerived then s"${f.tpe} derived from ${f.derivedFrom.mkString("+")}" else f.tpe)
        .distinct
      Violation(
        Rule.Reference,
        use.usedBy.map(_._2).mkString(", "),
        s"attribute '${use.name}' is defined ${use.definitions.size} different ways " +
          s"(${shapes.mkString("; ")}); one name has to mean one thing for a data path to be traceable",
        Some(Target(use.name))
      )

  // --- information completeness --------------------------------------------

  private def names(fields: List[Field]): Set[String] =
    fields.map(_.name).toSet

  /** Fields with no source.
    *
    * A derived field is satisfied by what it is derived *from*, so a sum can
    * be named for what it means rather than for the field it adds up. The
    * sources still have to exist -- a derivation that names nothing available
    * is reported like any other gap.
    */
  private def missing(required: List[Field], available: Set[String]): List[Field] =
    required.filterNot { f =>
      // Name match first: a derived field passed along by name (a read model
      // feeding a command, say) is present in the ordinary way, and only falls
      // back to its derivation when it has to be recomputed from parts.
      available.contains(f.name) ||
      (f.isDerived && f.derivedFrom.forall(available.contains))
    }

  /** Explains a gap in terms of the derivation, when there is one. */
  private def gapIn(f: Field): String =
    if f.isDerived then
      val absent = f.derivedFrom.mkString("', '")
      s"'${f.name}' is derived from '$absent', which nothing provides"
    else s"'${f.name}'"

  private def completeness(
      model: EventModel,
      slice: Slice,
      everyEvent: List[Event]
  ): List[Violation] =
    slice match
      case Slice.StateChange(name, command, events, screen, _, _) =>
        // A person can type things, so unaccounted command fields are not a
        // fault here -- but a screen nothing feeds is: a form cannot pre-fill
        // an id or a price out of thin air.
        val screenOrigin = screen.toList.flatMap { s =>
          if model.readModelsFeeding(s).nonEmpty then Nil
          else
            List(
              Violation(
                Rule.DataOrigin,
                name,
                s"screen '${s.name}' supplies command '${command.name}', but no " +
                  "State View feeds that screen, so nothing on it can be pre-filled",
                Some(Target(s.name))
              )
            )
        }
        screenOrigin ++
          eventsCoveredBy(name, events, names(command.fields), s"command '${command.name}'")

      case Slice.StateView(name, events, readModel, _, _, _) =>
        val available = names(events.flatMap(_.fields))
        missing(readModel.fields, available).map { f =>
          Violation(
            Rule.InformationCompleteness,
            name,
            s"read model '${readModel.name}' exposes ${gapIn(f)} but no source event provides it",
            Some(Target(readModel.name, f.name))
          )
        }

      case Slice.Automation(name, readModel, _, command, events, _, _, _) =>
        val fromAnyEvent = names(everyEvent.flatMap(_.fields))
        val readSide = missing(readModel.fields, fromAnyEvent).map { f =>
          Violation(
            Rule.InformationCompleteness,
            name,
            s"read model '${readModel.name}' exposes ${gapIn(f)} but no event in the model provides it",
            Some(Target(readModel.name, f.name))
          )
        }
        // Nobody types a command into an automation, so the read model it
        // watches is the only place its data can come from. Same derivation as
        // a State Change; the difference is that here there is no human to
        // fall back on, so anything unaccounted for is a gap.
        val commandOrigin = model.originOf(slice).toList.flatMap(_.unaccounted).map { f =>
          Violation(
            Rule.DataOrigin,
            name,
            s"command '${command.name}' needs '${f.name}', but read model " +
              s"'${readModel.name}' does not provide it and an automation has no other source",
            Some(Target(command.name, f.name))
          )
        }
        readSide ++ commandOrigin ++
          eventsCoveredBy(name, events, names(command.fields), s"command '${command.name}'")

      case Slice.Translation(name, external, style, events, readModel, _, _) =>
        val fromExternal = names(external.fields)
        style match
          case TranslationStyle.ToInternalEvent =>
            eventsCoveredBy(name, events, fromExternal, s"external event '${external.name}'")
          case TranslationStyle.ToReadModel =>
            readModel.toList.flatMap { rm =>
              missing(rm.fields, fromExternal).map { f =>
                Violation(
                  Rule.InformationCompleteness,
                  name,
                  s"read model '${rm.name}' exposes ${gapIn(f)} but external event '${external.name}' does not carry it",
                  Some(Target(rm.name, f.name))
                )
              }
            }

  private def eventsCoveredBy(
      slice: String,
      events: List[Event],
      available: Set[String],
      sourceLabel: String
  ): List[Violation] =
    for
      event <- events
      field <- missing(event.fields, available)
    yield Violation(
      Rule.InformationCompleteness,
      slice,
      s"event '${event.name}' records '${field.name}' but $sourceLabel does not supply it",
      Some(Target(event.name, field.name))
    )
