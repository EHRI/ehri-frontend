package models

import java.net.URI
import java.time.Instant

sealed trait OaiRsResource {
  def loc: URI
}
case class ResourceList(loc: URI) extends OaiRsResource
case class CapabilityList(loc: URI) extends OaiRsResource

case class ResourceLink(
  loc: URI,
  updated: Option[Instant] = None,
  length: Option[Long] = None,
  contentType: Option[String] = None,
  hash: Option[String] = None,
) extends OaiRsResource

