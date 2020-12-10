package models

import java.time.Instant

sealed trait ResourceSyncItem {
  def loc: String
}
case class ResourceList(loc: String) extends ResourceSyncItem
case class CapabilityList(loc: String) extends ResourceSyncItem

case class FileLink(
  loc: String,
  updated: Option[Instant] = None,
  length: Option[Long] = None,
  contentType: Option[String] = None,
  hash: Option[String] = None,
) extends ResourceSyncItem

