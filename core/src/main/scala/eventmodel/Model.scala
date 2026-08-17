package eventmodel

/** A group of slices within a chapter. The name may be empty for a chapter that
  * isn't sub-divided.
  */
final case class SubChapter(name: String, slices: List[Slice])

/** A logical grouping of slices, in the book's sense: "a chapter defines kind of
  * a context for a given slice".
  *
  * Chapters are drawn above the model so the reader's eye picks up the current
  * context while following the timeline. They are an optional extension to
  * Event Modeling rather than part of its original definition.
  */
final case class Chapter(
    name: String,
    description: String = "",
    subChapters: List[SubChapter] = Nil
)

object Chapter:
  /** A chapter with no sub-division. */
  def flat(name: String, slices: Slice*): Chapter =
    Chapter(name, subChapters = List(SubChapter("", slices.toList)))

/** A complete model: one business context, read left to right without visual
  * interruption.
  */
final case class EventModel(
    name: String,
    description: String = "",
    chapters: List[Chapter] = Nil,
    swimlanes: List[Swimlane] = Nil
):
  /** Every slice in reading order. This is the model's timeline. */
  def slices: List[Slice] =
    chapters.flatMap(_.subChapters).flatMap(_.slices)

  /** Which stream each event belongs to. Built once; every view uses it.
    *
    * An event in two lanes is a modeling error rather than something to
    * represent here, so the last lane wins and [[Validation]] reports it.
    */
  lazy val swimlaneOf: Map[Event, Swimlane] =
    (for lane <- swimlanes; event <- lane.events yield event -> lane).toMap

  /** Every slice flattened into the uniform shape renderers walk.
    *
    * Built once: this is the only place the four patterns are taken apart, and
    * everything downstream works from the result. See [[SliceView]].
    */
  lazy val views: List[SliceView] = slices.map(SliceView.of(_, this))

  /** Every event any slice touches, in reading order, without duplicates. */
  def eventsInUse: List[Event] =
    slices.flatMap(_.allEvents).distinct

  /** Models this one branches to, and why. */
  def altFlows: List[AltFlow] =
    slices.flatMap(_.altFlows)

  /** Read models that put data on a screen.
    *
    * Not declared anywhere -- a State View that shows this screen is, by
    * definition, what fills it in. The link is already in the model.
    */
  def readModelsFeeding(screen: Screen): List[ReadModel] =
    slices.collect {
      case sv: Slice.StateView if sv.screen.contains(screen) => sv.readModel
    }.distinct

  /** Every element that carries fields, as (kind, name, fields).
    *
    * Events come last within each slice, so an attribute is introduced by the
    * element that defines it before the events that carry it.
    */
  def elementsWithFields: List[(String, String, List[Field])] =
    views
      .flatMap { view =>
        view.nodes
          .filter(_.fields.nonEmpty)
          .sortBy(n => if n.kind == "event" then 1 else 0)
          .map(n => (n.label, n.name, n.fields))
      }
      .distinct

  /** Every attribute in the model and everywhere it appears.
    *
    * The method depends on one name meaning one thing throughout -- "we need to
    * define it consistently throughout the Event Model" -- but nothing can
    * enforce that automatically, since two uses of one name for two meanings
    * look identical to a checker. Listing them together is what makes the
    * question answerable by a person.
    */
  def glossary: List[AttributeUse] =
    elementsWithFields
      .flatMap((kind, element, fields) => fields.map(f => (f, kind, element)))
      .groupBy(_._1.name)
      .toList
      .sortBy(_._1)
      .map { (name, uses) =>
        AttributeUse(
          name,
          definitions = uses.map(_._1).distinct,
          usedBy = uses.map((_, kind, element) => (kind, element)).distinct
        )
      }

  /** Every screen in the model, in reading order. */
  def screens: List[Screen] = views.flatMap(_.screen).distinct

  /** What a screen has to work with, and what each of its commands still needs.
    *
    * Both sides are already in the model: State Views that show this screen
    * make information available to it, and State Changes that show it send
    * commands from it.
    */
  def usageOf(screen: Screen): ScreenUsage =
    val sources = readModelsFeeding(screen)
    val available = sources.flatMap(_.fields).distinctBy(_.name)
    val availableNames = available.map(_.name).toSet

    ScreenUsage(
      screen,
      sources,
      available,
      commands = slices.collect {
        case sc: Slice.StateChange if sc.screen.contains(screen) =>
          val (have, need) = sc.command.fields.partition(f => availableNames.contains(f.name))
          ScreenCommand(sc.name, sc.command, have, need)
      }
    )

  /** Where a slice's command gets its data.
    *
    * Empty for slices that issue no command. See [[CommandOrigin]] for what
    * `unaccounted` means, which differs by pattern.
    */
  def originOf(slice: Slice): Option[CommandOrigin] =
    def split(command: Command, sources: List[ReadModel]) =
      val available = sources.flatMap(_.fields).map(_.name).toSet
      val (supplied, unaccounted) = command.fields.partition(f => available.contains(f.name))
      Some(CommandOrigin(command, sources, supplied, unaccounted))

    slice match
      case sc: Slice.StateChange =>
        split(sc.command, sc.screen.toList.flatMap(readModelsFeeding))
      case a: Slice.Automation =>
        split(a.command, List(a.readModel))
      case _ => None

/** One attribute, and every element that carries it.
  *
  * `definitions` holds more than one entry when the same name is used with
  * different types or derivations, which is an inconsistency a checker can
  * catch. The subtler failure -- one name, one type, two meanings -- is only
  * visible to a person, which is what the glossary page is for.
  */
final case class AttributeUse(
    name: String,
    definitions: List[Field],
    usedBy: List[(String, String)]
):
  def isConflicted: Boolean = definitions.sizeIs > 1

/** One command a screen can send, and how much of it the screen already has.
  *
  * `mustCollect` is the useful number: what this screen has to get from
  * somewhere other than the read models behind it -- usually the person using
  * it.
  *
  * Matching is by field name, so a read model and a command that use one name
  * for two different things will look satisfied. That is worth knowing about
  * rather than working around: if they are different things, they want
  * different names.
  */
final case class ScreenCommand(
    slice: String,
    command: Command,
    alreadyAvailable: List[Field],
    mustCollect: List[Field]
)

/** A screen's contract, for whoever has to build it. */
final case class ScreenUsage(
    screen: Screen,
    sources: List[ReadModel],
    available: List[Field],
    commands: List[ScreenCommand]
):
  /** Everything any command on this screen still needs. */
  def mustCollect: List[Field] = commands.flatMap(_.mustCollect).distinctBy(_.name)

/** How much of a command the model can account for.
  *
  * `unaccounted` means something different either side of the human: for a
  * State Change it is what a person has to type, which is a fact worth stating
  * out loud; for an Automation there is nobody to type it, so it is a gap.
  */
final case class CommandOrigin(
    command: Command,
    sources: List[ReadModel],
    supplied: List[Field],
    unaccounted: List[Field]
)

/** Many small models beat one large one: each captures a single business
  * context you can read left to right without visual interruption.
  *
  * Models reached only as alternative flows do not need listing in [[models]];
  * [[allModels]] finds them.
  */
final case class Board(
    name: String,
    description: String = "",
    models: List[EventModel] = Nil
):
  /** Every model on the board, including alternative flows, breadth-first from
    * the declared ones.
    *
    * Deduplicated by name and cycle-safe: an error flow that links back to the
    * happy path it came from is a reasonable thing to draw.
    */
  def allModels: List[EventModel] =
    def loop(queue: List[EventModel], seen: Set[String], acc: List[EventModel]): List[EventModel] =
      queue match
        case Nil => acc.reverse
        case m :: rest if seen.contains(m.name) => loop(rest, seen, acc)
        case m :: rest =>
          loop(rest ++ m.altFlows.map(_.model), seen + m.name, m :: acc)

    loop(models, Set.empty, Nil)
