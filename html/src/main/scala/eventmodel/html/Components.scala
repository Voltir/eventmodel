package eventmodel.html

import eventmodel.*
import scalatags.Text.all.*

/** Which elements and fields a validation run implicated. */
final case class Flags(fieldPairs: Set[(String, String)], elements: Set[String]):
  def hasField(element: String, field: String): Boolean =
    fieldPairs.contains(element -> field)
  def hasElement(element: String): Boolean =
    elements.contains(element)

object Flags:
  val none: Flags = Flags(Set.empty, Set.empty)

  def from(violations: List[Violation]): Flags =
    Flags(
      violations
        .flatMap(v => v.target.filter(_.field.nonEmpty).map(t => t.element -> t.field))
        .toSet,
      violations.flatMap(_.target.map(_.element)).toSet
    )

/** `scope` disambiguates element ids on pages where the same event appears in
  * more than one slice, so anchors stay unique and linkable.
  */
final case class RenderCtx(scope: String, flags: Flags)

/** The sticky notes, shared by every view. Colours follow the book: commands
  * blue, events orange, read models green. Anything validation implicated is
  * marked, so a problem is visible on the model itself and not only in the
  * report above it.
  */
object Components:

  /** An empty scope means the page draws each element exactly once, so there is
    * nothing to disambiguate -- which is what the storyboard does.
    */
  def elementId(scope: String, element: String): String =
    if scope.isEmpty then s"el-${Site.slug(element)}"
    else s"el-${scope}-${Site.slug(element)}"

  def card(kind: String, title: String, fields: List[Field])(using ctx: RenderCtx): Frag =
    div(
      cls := s"card $kind${flagClass(ctx.flags.hasElement(title))}",
      id := elementId(ctx.scope, title),
      div(cls := "card-title", title),
      fieldList(title, fields)
    )

  def fieldList(element: String, fields: List[Field])(using ctx: RenderCtx): Frag =
    if fields.isEmpty then frag()
    else
      ul(
        cls := "fields",
        fields.map { f =>
          val flagged = ctx.flags.hasField(element, f.name)
          li(
            cls := (if flagged then "field flagged" else "field"),
            if flagged then span(cls := "warn-icon", title := "unresolved", "⚠") else frag(),
            span(cls := "fname", f.name),
            span(cls := "ftype", f.tpe),
            if f.isDerived then
              span(cls := "fnote", s"derived from ${f.derivedFrom.mkString(", ")}")
            else frag(),
            if f.note.isEmpty then frag() else span(cls := "fnote", f.note)
          )
        }
      )

  /** `to` links the title through to the screen's own page, which is where its
    * full contract lives. Relative, so each caller passes the path from where
    * it happens to sit.
    */
  def screenCard(s: Screen, to: String = "")(using ctx: RenderCtx): Frag =
    div(
      cls := "card screen",
      id := elementId(ctx.scope, s.name),
      div(
        cls := "card-title",
        if to.isEmpty then frag(s.name) else a(cls := "card-link", href := to, s.name)
      ),
      if s.description.isEmpty then frag() else div(cls := "desc", s.description)
    )

  /** `lane` is looked up from the model rather than stored on the event.
    *
    * `showStream` is off on the storyboard, where the row the card sits in
    * already is the stream and naming it again on every card is noise.
    */
  def eventCard(e: Event, lane: Option[Swimlane], showStream: Boolean = true)(using
      ctx: RenderCtx
  ): Frag =
    div(
      cls := s"card event${flagClass(ctx.flags.hasElement(e.name))}",
      id := elementId(ctx.scope, e.name),
      div(cls := "card-title", e.name),
      fieldList(e.name, e.fields),
      lane match
        case Some(l) => if showStream then div(cls := "stream", s"stream: ${l.name}") else frag()
        case None    => div(cls := "stream warn", "⚠ no swimlane")
    )

  def processorCard(p: Processor, trigger: Trigger)(using ctx: RenderCtx): Frag =
    div(
      cls := "card processor",
      id := elementId(ctx.scope, p.name),
      div(cls := "card-title", s"⚙ ${p.name}"),
      if p.description.isEmpty then frag() else div(cls := "desc", p.description),
      div(cls := "stream", trigger.label)
    )

  def externalCard(e: ExternalEvent, style: TranslationStyle)(using ctx: RenderCtx): Frag =
    div(
      cls := s"card external${flagClass(ctx.flags.hasElement(e.name))}",
      id := elementId(ctx.scope, e.name),
      div(cls := "card-title", e.name),
      div(cls := "desc", s"from ${e.source}"),
      fieldList(e.name, e.fields),
      div(cls := "stream", style.label)
    )

  private def flagClass(flagged: Boolean): String =
    if flagged then " flagged" else ""

  /** Any element of a slice, as its note.
    *
    * The one place card-building dispatches on what an element is. Callers walk
    * `SliceView.steps` and hand each node here rather than knowing which of the
    * four patterns they are looking at.
    */
  def nodeCard(n: Node, screenLink: String = "")(using RenderCtx): Frag = n match
    case Node.ScreenNode(s)          => screenCard(s, screenLink)
    case Node.CommandNode(c)         => card("command", c.name, c.fields)
    case Node.EventNode(e, lane)     => eventCard(e, lane)
    case Node.ReadModelNode(r)       => card("readmodel", r.name, r.fields)
    case Node.ProcessorNode(p, t)    => processorCard(p, t)
    case Node.ExternalNode(x, style) => externalCard(x, style)

  /** Swatches carry their own colours rather than reusing `.card`, which now
    * has padding and a shadow that a 0.85rem square cannot absorb.
    */
  def legend: Frag =
    def item(kind: String, label: String) =
      span(span(cls := s"swatch $kind"), label)
    div(
      cls := "legend",
      item("screen", "Screen"),
      item("command", "Command"),
      item("event", "Event"),
      item("readmodel", "Read Model"),
      item("external", "External Event")
    )

  /** `scope` names what was checked, so an empty list reads as reassurance
    * rather than an absence. `href` turns each violation into a link to the
    * thing it is about, where the page can offer one.
    */
  def violationsPanel(
      violations: List[Violation],
      scope: String,
      href: Violation => Option[String] = _ => None
  ): Frag =
    if violations.isEmpty then p(cls := "ok", s"No issues found in $scope.")
    else
      div(
        cls := "violations",
        h2(s"${violations.size} unresolved in $scope"),
        ul(
          violations.map { v =>
            val text = frag(
              span(cls := "rule-tag", v.rule.label),
              " ",
              v.detail,
              " ",
              code(s"(${v.slice})")
            )
            li(href(v) match
              case Some(target) => a(cls := "violation-link", scalatags.Text.all.href := target, text)
              case None         => text
            )
          }
        )
      )

  // --- given / when / then --------------------------------------------------

  def gwtBlock(slice: String, r: Gwt): Frag =
    scalatags.Text.tags2.details(
      cls := "rule",
      attr("data-slices") := slice.toLowerCase,
      scalatags.Text.tags2.summary(r.name, span(cls := "slice-of", s" — $slice")),
      dl(
        cls := "gwt",
        dt("Given"),
        if r.givenEvents.isEmpty then dd(cls := "ftype", "nothing yet")
        else frag(r.givenEvents.map(e => dd(e.name))),
        dt("When"),
        dd(r.whenCommand.name),
        dt("Then"),
        r.thenOutcome match
          case Outcome.Events(es)    => frag(es.map(e => dd(e.name)))
          case Outcome.Rejected(why) => dd(cls := "rejected", s"rejected: $why")
      )
    )

  def gtBlock(slice: String, r: Gt): Frag =
    scalatags.Text.tags2.details(
      cls := "rule",
      attr("data-slices") := slice.toLowerCase,
      scalatags.Text.tags2.summary(r.name, span(cls := "slice-of", s" — $slice")),
      dl(
        cls := "gwt",
        dt("Given"),
        frag(r.givenEvents.map(e => dd(e.name))),
        dt("Then"),
        dd(r.thenExpectation)
      )
    )

  def rulesOf(slice: Slice): List[Frag] = slice match
    case sc: Slice.StateChange => sc.rules.map(gwtBlock(sc.name, _))
    case a: Slice.Automation   => a.rules.map(gwtBlock(a.name, _))
    case t: Slice.Translation  => t.rules.map(gwtBlock(t.name, _))
    case sv: Slice.StateView   => sv.rules.map(gtBlock(sv.name, _))
