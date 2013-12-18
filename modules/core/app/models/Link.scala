package models

import models.base._
import defines.EntityType
import play.api.libs.json._
import models.json._
import play.api.libs.functional.syntax._
import play.api.i18n.Lang
import eu.ehri.project.definitions.Ontology


object LinkF {

  val LINK_TYPE = "type"
  val DESCRIPTION = "description"
  val ALLOW_PUBLIC = Ontology.IS_PROMOTABLE

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
  description: Option[String],
  isPromotable: Boolean = false
) extends Model with Persistable


object Link {
  implicit object Converter extends RestReadable[Link] with ClientConvertable[Link] {
    val restReads = models.json.LinkFormat.metaReads

    private implicit val linkFormat = Json.format[LinkF]
    val clientFormat: Format[Link] = (
      __.format[LinkF](LinkF.Converter.clientFormat) and
        nullableListFormat(__ \ "targets")(AnyModel.Converter.clientFormat) and
        (__ \ "user").lazyFormatNullable[UserProfile](UserProfile.Converter.clientFormat) and
        nullableListFormat(__ \ "accessPoints")(AccessPointF.Converter.clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        nullableListFormat(__ \ "promotedBy")(UserProfile.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
        (__ \ "meta").format[JsObject]
    )(Link.apply _, unlift(Link.unapply _))
  }

  implicit object Resource extends RestResource[Link] {
    val entityType = EntityType.Link
  }
}

case class Link(
  model: LinkF,
  targets: List[AnyModel] = Nil,
  user: Option[UserProfile] = None,
  bodies: List[AccessPointF] = Nil,
  accessors: List[Accessor] = Nil,
  promotors: List[UserProfile] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[LinkF] with Accessible with Promotable {
  def opposingTarget(item: AnyModel): Option[AnyModel] = opposingTarget(item.id)
  def opposingTarget(itemId: String): Option[AnyModel] = targets.find(_.id != itemId)

  override def toStringLang(implicit lang: Lang) = "Link: (" + id + ")"
}