package models

import models.base.AccessibleEntity
import models.base.Accessor
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

/**
 * Item that has an identifier and (optionally) a display name.
 * @param e
 */
case class ItemWithId(val e: Entity) extends AccessibleEntity {

  val NAME = "name"

  def name = e.stringProperty(NAME).getOrElse(e.id)
}

