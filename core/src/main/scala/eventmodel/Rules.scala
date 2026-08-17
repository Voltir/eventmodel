package eventmodel

/** What a state change is expected to produce.
  *
  * The book's own example ends "...THEN the system should raise an error", so
  * a rejection is a first-class outcome rather than an absence of events.
  */
enum Outcome:
  case Events(events: List[Event])
  case Rejected(reason: String)

/** GIVEN / WHEN / THEN -- a business rule attached to a State Change.
  *
  * "GIVEN something has already happened, WHEN this new thing happens, THEN we
  * expect the system to be in this new state."
  *
  * `given` and `then` are Scala 3 keywords, hence the longer names.
  */
final case class Gwt(
    name: String,
    givenEvents: List[Event],
    whenCommand: Command,
    thenOutcome: Outcome
)

/** GIVEN / THEN -- a business rule attached to a State View.
  *
  * Read models only ever read events that already happened, so there is no
  * WHEN. The book is explicit that these are GTs rather than GWTs.
  */
final case class Gt(
    name: String,
    givenEvents: List[Event],
    thenExpectation: String
)
