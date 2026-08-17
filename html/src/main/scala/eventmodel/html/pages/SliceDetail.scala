package eventmodel.html
package pages

import eventmodel.*
import scalatags.Text.all.*

/** One slice, in full: its elements, its business rules, and only the
  * violations that belong to it.
  */
object SliceDetail:

  def page(model: EventModel, view: SliceView, all: List[Violation], dir: String): Page =
    val slice = view.slice
    val mine  = all.filter(_.slice == view.name)
    given RenderCtx = RenderCtx(Site.slug(view.name), Flags.from(all))
    Page(
      s"$dir/slice/${Site.slug(view.name)}.html",
      view.name,
      frag(
        h1(view.name),
        p(cls := "subtitle", span(cls := "badge", view.pattern), " ", context(model, slice)),
        Components.violationsPanel(mine, "this slice", Site.anchorFor(Site.bySlice)(_).map("#" + _)),
        div(cls := "slice-detail", elements(view)),
        dataOrigin(model, slice),
        altFlows(slice),
        rules(slice)
      ),
      model = Some(model)
    )

  /** Where the command's data comes from.
    *
    * Only asked of automations. A State Change is driven by a person at a
    * screen, and deciding per field whether it was displayed or typed is not
    * something field names can settle -- a pre-filled form field is both. The
    * screen page states both lists instead.
    */
  private def dataOrigin(model: EventModel, slice: Slice): Frag =
    slice match
      case _: Slice.Automation =>
        model.originOf(slice) match
          case None => frag()
          case Some(origin) =>
            div(
              cls := "outline",
              h2("Where the data comes from"),
              p(
                cls := "subtitle",
                "An automation has no one to type anything, so its read model is " +
                  "the only source it has."
              ),
              p(
                cls := "subtitle",
                "fed by ",
                origin.sources.map(rm => span(cls := "inline-tag", rm.name))
              ),
              ul(
                cls := "origin",
                origin.supplied.map { f =>
                  li(span(cls := "fname", f.name), span(cls := "ftype", "from the read model"))
                },
                origin.unaccounted.map { f =>
                  li(
                    cls := "field flagged",
                    span(cls := "warn-icon", "⚠"),
                    span(cls := "fname", f.name),
                    span(cls := "ftype", "no source")
                  )
                }
              )
            )

      case sc: Slice.StateChange =>
        sc.screen match
          case None => frag()
          case Some(s) =>
            p(
              cls := "subtitle",
              "Sent from ",
              a(href := s"../screen/${Site.slug(s.name)}.html", s.name),
              " — see that screen for everything it displays and captures."
            )

      case _ => frag()

  /** From `<dir>/slice/<name>.html`, another model is two levels up. */
  private def altFlows(slice: Slice): Frag =
    if slice.altFlows.isEmpty then frag()
    else
      div(
        cls := "outline",
        h2("Alternative flows"),
        p(
          cls := "subtitle",
          "Event Modeling follows one path along one timeline. These are the " +
            "others, each modelled separately."
        ),
        ul(
          cls := "slice-list",
          slice.altFlows.map { alt =>
            li(
              span(cls := "altflow-icon", "⤳"),
              a(href := s"../../${Site.dirOf(alt.model)}/storyboard.html", alt.model.name),
              span(cls := "subtitle", alt.reason)
            )
          }
        )
      )

  /** Which chapter and sub-chapter this slice sits under. */
  private def context(model: EventModel, slice: Slice): String =
    val found =
      for
        ch <- model.chapters
        sub <- ch.subChapters
        if sub.slices.contains(slice)
      yield if sub.name.isEmpty then ch.name else s"${ch.name} → ${sub.name}"
    found.headOption.getOrElse("")

  /** The slice's elements in reading order, consecutive ones of a kind grouped
    * under a heading.
    *
    * No dispatch on the pattern: `SliceView.steps` already put the elements in
    * the order this page wants them, which is the same order the storyboard
    * reads left to right.
    */
  private def elements(view: SliceView)(using RenderCtx): Frag =
    val groups = view.steps.map(_.node).foldRight(List.empty[(String, List[Node])]) {
      case (n, (kind, group) :: rest) if kind == n.kind => (kind, n :: group) :: rest
      case (n, rest)                                    => (n.kind, List(n)) :: rest
    }

    frag(
      groups.map { (kind, nodes) =>
        div(
          cls := "group",
          h2(heading(view.pattern, kind)),
          // From `<dir>/slice/<name>.html`, a screen page is one level up.
          nodes.map(n => Components.nodeCard(n, s"../screen/${Site.slug(n.name)}.html"))
        )
      }
    )

  /** An automation watches a read model rather than publishing one, and saying
    * so is the difference between reading the page and decoding it.
    */
  private def heading(pattern: String, kind: String): String =
    if pattern == "Automation" && kind == "readmodel" then "Watches"
    else
      kind match
        case "screen"    => "Screen"
        case "command"   => "Command"
        case "event"     => "Events"
        case "readmodel" => "Read Model"
        case "processor" => "Process"
        case _           => "External"

  private def rules(slice: Slice): Frag =
    val blocks = Components.rulesOf(slice)
    if blocks.isEmpty then
      p(cls := "ok", "No business rules recorded for this slice yet.")
    else div(cls := "rules", h2("Business rules"), blocks)
