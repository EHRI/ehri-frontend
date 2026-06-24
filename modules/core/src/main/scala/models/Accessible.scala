package models

import play.api.libs.json.JsTrue


trait Accessible extends Model {
  /**
    * Get the set of accessors to whom this item is visible.
    */
  def accessors: Seq[Accessor]

  /**
    * Determine if a given item is private to a particular user.
    */
  def privateTo(accessor: Accessor): Boolean =
    accessors.size == 1 && accessors.head.id == accessor.id

  /**
    * Get the latest event for this item.
    */
  def latestEvent: Option[SystemEvent]

  /**
    * Check if this item has a prior version
    */
  def isVersioned: Boolean = (meta \ "isVersioned").toOption.contains(JsTrue)
}
