package eventmodel.html
package pages

import eventmodel.*
import scalatags.Text.all.*

/** A screen's contract, aimed at whoever has to build it.
  *
  * Both halves are derived: the read models that fill a screen are the State
  * Views that show it, and the commands it sends are the State Changes that
  * show it. Nothing decides whether a field is "displayed" or "typed" -- a
  * pre-filled form field is both, and a frontend needs the two lists anyway.
  */
object ScreenPage:

  def page(model: EventModel, screen: Screen, dir: String): Page =
    val usage = model.usageOf(screen)

    Page(
      s"$dir/screen/${Site.slug(screen.name)}.html",
      screen.name,
      frag(
        h1(screen.name),
        if screen.description.isEmpty then frag()
        else p(cls := "subtitle", screen.description),
        displays(usage),
        captures(usage),
        appearsIn(model, screen)
      ),
      model = Some(model)
    )

  private def displays(usage: ScreenUsage) =
    div(
      cls := "outline",
      h2("Information available"),
      if usage.sources.isEmpty then
        p(
          cls := "warn-inline",
          "⚠ no State View feeds this screen, so it has nothing to work with."
        )
      else
        frag(
          p(
            cls := "subtitle",
            "The screen can show any of this. It comes from ",
            usage.sources.map(rm => span(cls := "inline-tag", rm.name))
          ),
          ul(cls := "origin", usage.available.map(row))
        )
    )

  private def captures(usage: ScreenUsage) =
    div(
      cls := "outline",
      h2("Commands it sends"),
      if usage.commands.isEmpty then
        p(cls := "subtitle", "This screen sends no commands; it is read-only.")
      else
        frag(
          p(
            cls := "subtitle",
            "For each one, what the screen still has to collect -- normally " +
              "from the person using it."
          ),
          usage.commands.map { sc =>
            div(
              cls := "capture",
              // Slices are usually named after their command, so only say
              // where it came from when that tells you something.
              h3(
                a(href := s"../slice/${Site.slug(sc.slice)}.html", sc.command.name),
                if sc.slice == sc.command.name then frag()
                else span(cls := "subtitle", s" via ${sc.slice}")
              ),
              if sc.mustCollect.isEmpty then
                p(cls := "subtitle", "Nothing to collect — everything is already available.")
              else
                ul(
                  cls := "origin",
                  sc.mustCollect.map { f =>
                    li(
                      cls := "form-input",
                      span(cls := "fname", f.name),
                      span(cls := "ftype", f.tpe),
                      span(cls := "collect", "must be collected here")
                    )
                  }
                ),
              if sc.alreadyAvailable.isEmpty then frag()
              else
                p(
                  cls := "subtitle already",
                  "already available: ",
                  sc.alreadyAvailable.map(f => span(cls := "inline-tag", f.name))
                )
            )
          }
        )
    )

  private def row(f: Field) =
    li(
      span(cls := "fname", f.name),
      span(cls := "ftype", f.tpe),
      if f.note.isEmpty then frag() else span(cls := "fnote", f.note)
    )

  private def appearsIn(model: EventModel, screen: Screen) =
    val slices = model.slices.filter {
      case sc: Slice.StateChange => sc.screen.contains(screen)
      case sv: Slice.StateView   => sv.screen.contains(screen)
      case _                     => false
    }
    div(
      cls := "outline",
      h2("Appears in"),
      ul(
        cls := "slice-list",
        slices.map { s =>
          li(
            a(href := s"../slice/${Site.slug(s.name)}.html", s.name),
            span(cls := "badge", s.pattern)
          )
        }
      )
    )
