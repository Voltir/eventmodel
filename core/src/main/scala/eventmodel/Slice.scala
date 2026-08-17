package eventmodel

/** How a translation is drawn. The book notes this is the one pattern with
  * real variance in how people model it.
  */
enum TranslationStyle:
  /** External event is explicitly translated and stored as a new internal
    * event via a state change. The book's usual preference.
    */
  case ToInternalEvent

  /** External events are represented directly as a read model, translating
    * under the covers. Simpler when the external data already matches.
    */
  case ToReadModel

  def label: String = this match
    case ToInternalEvent => "translated to internal event"
    case ToReadModel     => "surfaced directly as read model"

/** A link from a slice to a model covering a different path through it.
  *
  * Event Modeling follows one use case along one timeline; conditions and loops
  * are not drawn. Error cases become their own model, reached from a marker
  * under the slice they branch from.
  */
final case class AltFlow(reason: String, model: EventModel)

/** One vertical slice of the model: a single step, in one of the four
  * patterns Event Modeling allows.
  */
enum Slice:
  def name: String
  def altFlows: List[AltFlow]

  /** Screen -> Command -> Event. The only way to trigger change in a system. */
  case StateChange(
      name: String,
      command: Command,
      events: List[Event],
      screen: Option[Screen] = None,
      rules: List[Gwt] = Nil,
      altFlows: List[AltFlow] = Nil
  )

  /** Event(s) -> Read Model -> screen or background process. Pulls data out
    * without saying how.
    */
  case StateView(
      name: String,
      events: List[Event],
      readModel: ReadModel,
      screen: Option[Screen] = None,
      rules: List[Gt] = Nil,
      altFlows: List[AltFlow] = Nil
  )

  /** A State View plus a State Change plus a gear: something watches a read
    * model and issues a command with no human involved.
    */
  case Automation(
      name: String,
      readModel: ReadModel,
      processor: Processor,
      command: Command,
      events: List[Event],
      trigger: Trigger = Trigger.ByEvent,
      rules: List[Gwt] = Nil,
      altFlows: List[AltFlow] = Nil
  )

  /** Communication across the system boundary, via an external event. */
  case Translation(
      name: String,
      external: ExternalEvent,
      style: TranslationStyle = TranslationStyle.ToInternalEvent,
      events: List[Event] = Nil,
      readModel: Option[ReadModel] = None,
      rules: List[Gwt] = Nil,
      altFlows: List[AltFlow] = Nil
  )

object Slice:
  extension (s: Slice)
    /** The pattern name as the book uses it. */
    def pattern: String = s match
      case _: StateChange => "State Change"
      case _: StateView   => "State View"
      case _: Automation  => "Automation"
      case _: Translation => "Translation"

    /** Every event this slice produces or reads, for stream/swimlane views. */
    def allEvents: List[Event] = s match
      case sc: StateChange => sc.events
      case sv: StateView   => sv.events
      case a: Automation   => a.events
      case t: Translation  => t.events

