package eventmodel.render

import eventmodel.EventModel

/** Turning names into paths. Shared, so a link written by one backend and a
  * file written by another agree on what a model is called.
  */
object Paths:

  def slug(s: String): String =
    val cleaned = s.trim.toLowerCase.map(c => if c.isLetterOrDigit then c else '-')
    cleaned.split("-").filter(_.nonEmpty).mkString("-")

  /** Each model gets its own name in the output, so a board is a set of small
    * documents that reference each other.
    */
  def dirOf(model: EventModel): String = slug(model.name)
