package eventmodel.html
package pages

import eventmodel.*
import scalatags.Text.all.*

/** Every attribute in the model, and everywhere it appears.
  *
  * The method leans on one name meaning one thing throughout, and no checker
  * can confirm that: two uses of a name for two different things look
  * identical to a machine. Putting them side by side is what makes the question
  * answerable -- if `quantity` appears on both a command and a read model, is
  * that the same quantity?
  */
object Glossary:

  def page(model: EventModel, dir: String): Page =
    val entries = model.glossary
    Page(
      s"$dir/glossary.html",
      "Glossary",
      frag(
        h1("Glossary"),
        p(
          cls := "subtitle",
          "Every attribute and where it is used. One name should mean one thing " +
            "everywhere -- that is what makes a data path traceable. Read the " +
            "shared ones and ask whether they really are the same thing."
        ),
        if entries.isEmpty then p(cls := "ok", "No attributes yet.")
        else div(cls := "glossary", entries.map(entry))
      ),
      model = Some(model)
    )

  private def entry(use: AttributeUse) =
    div(
      cls := s"glossary-entry${if use.isConflicted then " flagged" else ""}",
      h3(
        if use.isConflicted then span(cls := "warn-icon", "⚠") else frag(),
        span(cls := "fname", use.name),
        use.definitions.map(d => span(cls := "inline-tag", d.tpe))
      ),
      use.definitions.filter(_.isDerived).map { d =>
        p(cls := "subtitle", s"derived from ${d.derivedFrom.mkString(", ")}")
      },
      use.definitions.filter(_.note.nonEmpty).map(d => p(cls := "subtitle", d.note)),
      // Shared attributes are the interesting ones: that is where a name may
      // be doing two jobs.
      ul(
        cls := "usedby",
        use.usedBy.map { (kind, element) =>
          li(span(cls := "kind", kind), span(element))
        }
      )
    )
