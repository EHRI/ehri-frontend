package models.view

import models.{Annotation, Link, Model}
import utils.Page

case class ItemDetails(
  annotations: Page[Annotation],
  links: Page[Link],
  watched: Seq[String] = Nil
) {
  def isWatching(item: Model): Boolean = watched.contains(item.id)
}
