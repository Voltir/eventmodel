package eventmodel.html
package pages

import eventmodel.*
import scalatags.Text.all.*

/** The model's home page: what is in it, and what is wrong with it. */
object Index:

  def page(model: EventModel, violations: List[Violation], dir: String): Page =
    Page(
      s"$dir/index.html",
      "Overview",
      frag(
        h1(model.name),
        if model.description.isEmpty then frag()
        else p(cls := "subtitle", model.description),
        // Nothing is rendered on this page to anchor to, so send the reader to
        // the slice page and let :target flash the card there.
        Components.violationsPanel(
          violations,
          "this model",
          v => Site.anchorFor(Site.bySlice)(v).map(a => s"slice/${Site.slug(v.slice)}.html#$a")
        ),
        p(a(cls := "cta", href := "storyboard.html", "Open the storyboard →")),
        chapters(model),
        screens(model),
        streams(model)
      ),
      model = Some(model)
    )

  private def chapters(model: EventModel) =
    div(
      cls := "outline",
      h2("Chapters"),
      model.chapters.map { ch =>
        div(
          cls := "chapter",
          h3(ch.name),
          if ch.description.isEmpty then frag() else p(cls := "subtitle", ch.description),
          ch.subChapters.map { sub =>
            div(
              cls := "subchapter",
              if sub.name.isEmpty then frag() else h4(sub.name),
              ul(
                cls := "slice-list",
                sub.slices.map { s =>
                  li(
                    a(href := s"slice/${Site.slug(s.name)}.html", s.name),
                    span(cls := "badge", s.pattern)
                  )
                }
              )
            )
          }
        )
      }
    )

  private def screens(model: EventModel) =
    if model.screens.isEmpty then frag()
    else
      div(
        cls := "outline",
        h2("Screens"),
        p(
          cls := "subtitle",
          "What each screen has to display and capture, derived from the slices " +
            "it appears in. Useful to hand to whoever is building it."
        ),
        ul(
          cls := "slice-list",
          model.screens.map { s =>
            val usage = model.usageOf(s)
            li(
              a(href := s"screen/${Site.slug(s.name)}.html", s.name),
              span(cls := "badge", s"${usage.available.size} available"),
              span(cls := "badge", s"${usage.mustCollect.size} to collect")
            )
          }
        )
      )

  private def streams(model: EventModel) =
    div(
      cls := "outline",
      h2("Streams"),
      p(
        cls := "subtitle",
        "Each swimlane is a stream boundary. Read one in isolation: the events " +
          "should form a coherent story on their own."
      ),
      ul(
        cls := "slice-list",
        model.swimlanes.map { lane =>
          li(
            a(href := s"stream/${Site.slug(lane.name)}.html", lane.name),
            span(cls := "badge", s"${lane.events.size} events"),
            if lane.description.isEmpty then frag()
            else span(cls := "subtitle", s" ${lane.description}")
          )
        }
      )
    )
