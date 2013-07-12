package models

import models.base._
import defines.EntityType
import play.api.libs.json._
import models.json._
import play.api.libs.functional.syntax._


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

  implicit object Converter extends RestConvertable[LinkF] with ClientConvertable[LinkF] {
    lazy val restFormat = models.json.LinkFormat.restFormat
    lazy val clientFormat = Json.format[LinkF]
  }
}

case class LinkF(
  isA: EntityType.Value = EntityType.Link,
  id: Option[String],
  linkType: LinkF.LinkType.Type,
  description: Option[String]
) extends Model with Persistable


object Link {
  implicit object Converter extends RestReadable[Link] with ClientConvertable[Link] {
    private implicit val linkFormat = Json.format[LinkF]

    implicit val restReads = models.json.LinkFormat.metaReads
    //implicit val clientFormat = models.json.client.linkMetaFormat

    implicit val clientFormat: Format[Link] = (
      __.format[LinkF](LinkF.Converter.clientFormat) and
        nullableListFormat(__ \ "targets")(AnyModel.Converter.clientFormat) and
        (__ \ "user").lazyFormatNullable[UserProfile](UserProfile.Converter.clientFormat) and
        nullableListFormat(__ \ "accessPoints")(AccessPointF.Converter.clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
      )(Link.apply _, unlift(Link.unapply _))


  }
}

case class Link(
  model: LinkF,
  targets: List[AnyModel] = Nil,
  user: Option[UserProfile] = None,
  bodies: List[AccessPointF] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None
) extends AnyModel
  with MetaModel[LinkF] with Accessible {
  def opposingTarget(item: AnyModel): Option[AnyModel] = targets.find(_.id != item.id)
}