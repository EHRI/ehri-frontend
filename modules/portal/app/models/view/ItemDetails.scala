package models.view

import models.base.AnyModel
import models.{Annotation, Link}
import utils.Page

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class ItemDetails(
  annotations: Page[Annotation],
  links: Page[Link],
  watched: Seq[String] = Nil
) {
  def isWatching(item: AnyModel): Boolean = watched.contains(item.id)
}
