package eventmodel

/** A named piece of data carried by a command, event, or read model.
  *
  * `tpe` is a free-form string on purpose. These models describe a system at
  * the whiteboard level, and pinning fields to real Scala types would make them
  * harder to write than the sticky notes they replace.
  */
final case class Field(
    name: String,
    tpe: String,
    note: String = "",
    derivedFrom: List[String] = Nil
):
  /** A read model field is often computed rather than copied -- a sum, a count,
    * a latest-wins. Naming it accurately then makes it look unsourced, so say
    * what it is built from instead.
    *
    * The named fields still have to exist somewhere, so this states a
    * derivation rather than excusing one.
    */
  def isDerived: Boolean = derivedFrom.nonEmpty

/** A screen mockup. Deliberately crude in the book's method -- its job is to
  * anchor a discussion, not to be a design.
  */
final case class Screen(name: String, description: String = "")

/** An intent to change the system, triggered by a click or other interaction.
  * Commands may be rejected, and must carry all data needed to persist the
  * resulting event.
  */
final case class Command(name: String, fields: List[Field] = Nil)

/** A fact -- something that happened. Named in the past tense, never rejected.
  *
  * An event does not name its own stream. Streams own their events, because a
  * swimlane *is* a stream boundary -- see [[Swimlane]].
  */
final case class Event(name: String, fields: List[Field] = Nil)

/** Data arriving from outside the system boundary -- an API call, a Kafka
  * record, a CSV on a network share. The transport is deliberately not modeled.
  */
final case class ExternalEvent(
    name: String,
    source: String,
    fields: List[Field] = Nil
)

/** A query against events already stored in the system. Feeds screens and
  * background processes alike.
  *
  * The only data a read model may expose is data some event already carries --
  * see [[Completeness]].
  */
final case class ReadModel(name: String, fields: List[Field] = Nil)

/** A background process. The book draws this as a gear symbol and calls the
  * whole pattern an "automation"; this type is just the gear itself.
  */
final case class Processor(name: String, description: String = "")

/** What sets an automation running. */
enum Trigger:
  case ByEvent
  case ByTimer
  case ByUserInteraction

  def label: String = this match
    case ByEvent           => "triggered by event"
    case ByTimer           => "triggered by timer"
    case ByUserInteraction => "triggered by user interaction"

/** A stream boundary, usually one per business capability.
  *
  * The lane owns its events rather than events naming their lane: "Every stream
  * has a unique identifier... Swimlanes define stream boundaries. Typically, all
  * events in one swimlane end up in a physical stream."
  *
  * An event therefore belongs to exactly one lane. If what looks like the same
  * event appears in a second lane, it is a different event in a different
  * stream -- which is what the Translation pattern is for. [[Validation]]
  * enforces this.
  */
final case class Swimlane(
    name: String,
    description: String = "",
    events: List[Event] = Nil
)
