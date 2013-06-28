package models

import models.base._
import defines.EntityType
import play.api.libs.json.Format
import models.json.{RestReadable, ClientConvertable, RestConvertable}


object LinkF {

  val LINK_TYPE = "type"
  val DESCRIPTION = "description"

  final val LINK_REL = "hasLinkTarget"
  final val ACCESSOR_REL = "hasLinker"
  final val BODY_REL = "hasLinkBody"

  object LinkType extends Enumeration {
    type Type = Value
    val Hierarchical = Value("hierarchical")
    val Associative = Value("associative")
    val Family = Value("family")
    val Temporal = Value("temporal")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  lazy implicit val linkFormat: Format[LinkF] = json.LinkFormat.restFormat

  implicit object Converter extends RestConvertable[LinkF] with ClientConvertable[LinkF] {
    lazy val restFormat = models.json.rest.linkFormat
    lazy val clientFormat = models.json.client.linkFormat
  }
}

case class LinkF(
  isA: EntityType.Value = EntityType.Link,
  id: Option[String],
  linkType: LinkF.LinkType.Type,
  description: Option[String]
) extends Model with Persistable


object LinkMeta {
  implicit object Converter extends RestReadable[LinkMeta] with ClientConvertable[LinkMeta] {
    implicit val restReads = models.json.LinkFormat.metaReads
    implicit val clientFormat = models.json.client.linkMetaFormat
  }
}

case class LinkMeta(
  model: LinkF,
  targets: List[AnyModel] = Nil,
  user: Option[UserProfileMeta] = None,
  bodies: List[AccessPointF] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta] = None
) extends AnyModel
  with MetaModel[LinkF] with Accessible {
  def opposingTarget(item: AnyModel): Option[AnyModel] = targets.find(_.id != item.id)
}