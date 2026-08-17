package eventmodel

/** One element of a slice, together with the role it plays there.
  *
  * The four [[Slice]] patterns hold different elements in different fields, and
  * every consumer that walks a model -- a renderer, an exporter -- otherwise
  * has to restate the shape of all four. [[Node]] is the uniform shape they
  * walk instead.
  */
enum Node:
  case ScreenNode(screen: Screen)
  case CommandNode(command: Command)
  case EventNode(event: Event, lane: Option[Swimlane])
  case ReadModelNode(readModel: ReadModel)
  case ProcessorNode(processor: Processor, trigger: Trigger)
  case ExternalNode(external: ExternalEvent, style: TranslationStyle)

object Node:
  extension (n: Node)
    /** A stable tag, used for CSS classes and for labelling in plain text. */
    def kind: String = n match
      case _: ScreenNode    => "screen"
      case _: CommandNode   => "command"
      case _: EventNode     => "event"
      case _: ReadModelNode => "readmodel"
      case _: ProcessorNode => "processor"
      case _: ExternalNode  => "external"

    /** [[kind]] is a tag for machines; this is the phrase a person reads. */
    def label: String = n.kind match
      case "readmodel" => "read model"
      case "external"  => "external event"
      case other       => other

    def name: String = n match
      case ScreenNode(s)       => s.name
      case CommandNode(c)      => c.name
      case EventNode(e, _)     => e.name
      case ReadModelNode(r)    => r.name
      case ProcessorNode(p, _) => p.name
      case ExternalNode(x, _)  => x.name

    /** Empty for screens and processors, which carry no data of their own. */
    def fields: List[Field] = n match
      case CommandNode(c)     => c.fields
      case EventNode(e, _)    => e.fields
      case ReadModelNode(r)   => r.fields
      case ExternalNode(x, _) => x.fields
      case _                  => Nil

    /** The one extra fact worth stating beside the name, or empty. */
    def detail: String = n match
      case ScreenNode(s)             => s.description
      case ProcessorNode(p, trigger) => if p.description.isEmpty then trigger.label else p.description
      case ExternalNode(x, _)        => s"from ${x.source}"
      case EventNode(_, Some(lane))  => s"stream: ${lane.name}"
      case _                         => ""

/** An element of a slice, and whether the slice brings it into existence.
  *
  * `emits` is the difference between producing an element and reading one. A
  * State View's events were emitted by earlier slices; it only reads them,
  * which is why the storyboard draws an arrow back to them rather than a second
  * copy of the same sticky note.
  */
final case class Step(node: Node, emits: Boolean)

/** A slice, flattened into a uniform shape.
  *
  * `steps` is reading order within the slice; `flow` is how those elements
  * connect, which is what the book draws as arrows.
  */
final case class SliceView(
    slice: Slice,
    name: String,
    pattern: String,
    steps: List[Step],
    flow: List[(Node, Node)],
    branchFrom: Node,
    altFlows: List[AltFlow]
):
  def nodes: List[Node] = steps.map(_.node)

  /** Elements this slice brings into existence, which is where they belong on
    * the timeline.
    */
  def emitted: List[Node] = steps.filter(_.emits).map(_.node)

  def screen: Option[Screen] = steps.collectFirst { case Step(Node.ScreenNode(s), _) => s }

object SliceView:

  /** The one place the four patterns are spelled out.
    *
    * Everything downstream -- both renderers, and anything added later -- works
    * from the result rather than matching on [[Slice]] again. [[Validation]] is
    * the deliberate exception: its four branches encode genuinely different
    * rules, not a different arrangement of the same elements.
    */
  def of(slice: Slice, model: EventModel): SliceView =
    import Node.*

    def event(e: Event) = EventNode(e, model.swimlaneOf.get(e))

    val (steps, flow, branchFrom) = slice match
      case sc: Slice.StateChange =>
        val screen  = sc.screen.map(ScreenNode(_))
        val command = CommandNode(sc.command)
        val events  = sc.events.map(event)
        (
          screen.map(Step(_, emits = false)).toList ++
            (Step(command, emits = true) :: events.map(Step(_, emits = true))),
          screen.map(_ -> command).toList ++ events.map(command -> _),
          command
        )

      case sv: Slice.StateView =>
        // Read, not emitted: these events belong to the slices that produced
        // them, and this one reaches back for them.
        val events    = sv.events.map(event)
        val readModel = ReadModelNode(sv.readModel)
        val screen    = sv.screen.map(ScreenNode(_))
        (
          events.map(Step(_, emits = false)) ++
            (Step(readModel, emits = true) :: screen.map(Step(_, emits = false)).toList),
          events.map(_ -> readModel) ++ screen.map(readModel -> _).toList,
          readModel
        )

      case a: Slice.Automation =>
        val readModel = ReadModelNode(a.readModel)
        val processor = ProcessorNode(a.processor, a.trigger)
        val command   = CommandNode(a.command)
        val events    = a.events.map(event)
        (
          List(readModel, processor, command).map(Step(_, emits = true)) ++
            events.map(Step(_, emits = true)),
          List(readModel -> processor, processor -> command) ++ events.map(command -> _),
          command
        )

      case t: Slice.Translation =>
        val external  = ExternalNode(t.external, t.style)
        val events    = t.events.map(event)
        val readModel = t.readModel.map(ReadModelNode(_))
        (
          Step(external, emits = true) :: events.map(Step(_, emits = true)) ++
            readModel.map(Step(_, emits = true)).toList,
          events.map(external -> _) ++ readModel.map(external -> _).toList,
          external
        )

    SliceView(slice, slice.name, slice.pattern, steps, flow, branchFrom, slice.altFlows)
