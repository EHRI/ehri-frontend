package models

import models.base._
import defines.EntityType
import play.api.libs.json._
import models.json._
import play.api.i18n.Lang
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._


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

  import models.Entity._
  import Ontology._
  import play.api.libs.functional.syntax._

  implicit val linkWrites: Writes[LinkF] = new Writes[LinkF] {
    def writes(d: LinkF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          LINK_TYPE -> d.linkType,
          DESCRIPTION -> d.description,
          IS_PROMOTABLE -> d.isPromotable
        )
      )
    }
  }

  implicit val linkReads: Reads[LinkF] = (
    (__ \ TYPE).readIfEquals(EntityType.Link) and
    (__ \ ID).readNullable[String] and
    ((__ \ DATA \ LINK_TYPE).read[LinkType.Value]
      orElse Reads.pure(LinkType.Associative)) and
    (__ \ DATA \ DESCRIPTION).readNullable[String] and
    (__ \ DATA \ IS_PROMOTABLE).readNullable[Boolean].map(_.getOrElse(false))
  )(LinkF.apply _)

  implicit val linkFormat: Format[LinkF] = Format(linkReads,linkWrites)

  implicit object Converter extends RestConvertable[LinkF] with ClientConvertable[LinkF] {
    lazy val restFormat = linkFormat
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
  import models.Entity._
  import Ontology._
  import play.api.libs.functional.syntax._

  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = models.UserProfile.Converter.restReads
  private implicit val accessPointReads = models.AccessPointF.accessPointReads
  private implicit val systemEventReads = SystemEvent.Converter.restReads

  implicit val metaReads: Reads[Link] = (
    __.read[LinkF] and
    (__ \ RELATIONSHIPS \ LINK_HAS_TARGET).lazyNullableListReads(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ LINK_HAS_LINKER).nullableHeadReads[UserProfile] and
    (__ \ RELATIONSHIPS \ LINK_HAS_BODY).nullableListReads[AccessPointF] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyNullableListReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PROMOTED_BY).nullableListReads[UserProfile] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Link.apply _)

  implicit object Converter extends RestReadable[Link] with ClientConvertable[Link] {
    val restReads = metaReads

    private implicit val linkFormat = Json.format[LinkF]
    val clientFormat: Format[Link] = (
      __.format[LinkF](LinkF.Converter.clientFormat) and
      (__ \ "targets").nullableListFormat(AnyModel.Converter.clientFormat) and
      (__ \ "user").lazyFormatNullable[UserProfile](UserProfile.Converter.clientFormat) and
      (__ \ "accessPoints").nullableListFormat(AccessPointF.Converter.clientFormat) and
      (__ \ "accessibleTo").nullableListFormat(Accessor.Converter.clientFormat) and
      (__ \ "promotedBy").nullableListFormat(UserProfile.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Link.apply _, unlift(Link.unapply _))
  }

  implicit object Resource extends RestResource[Link] {
    val entityType = EntityType.Link
  }

  import LinkF._

  val form = Form(mapping(
    Entity.ISA -> ignored(EntityType.Link),
    Entity.ID -> optional(nonEmptyText),
    LINK_TYPE -> models.forms.enum(LinkType),
    DESCRIPTION -> optional(nonEmptyText), // TODO: Validate this server side
    Ontology.IS_PROMOTABLE -> default(boolean, false)
  )(LinkF.apply)(LinkF.unapply))

  val multiForm = Form(    single(
    "link" -> list(tuple(
      "id" -> nonEmptyText,
      "data" -> form.mapping,
      "accessPoint" -> optional(nonEmptyText)
    ))
  ))
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