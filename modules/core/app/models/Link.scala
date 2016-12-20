package models

import models.base._
import defines.{ContentTypes, EntityType}
import play.api.libs.json._
import models.json._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import backend._
import play.api.libs.json.JsObject


object LinkF {

  val LINK_TYPE = "type"
  val DESCRIPTION = "description"
  val ALLOW_PUBLIC = Ontology.IS_PROMOTABLE
  val DATES = "dates"

  object LinkType extends Enumeration {
    type Type = Value
    val Identity = Value("identity")
    val Associative = Value("associative")
    val Family = Value("family")
    val Hierarchical = Value("hierarchical")
    val Temporal = Value("temporal")

    implicit val format: Format[LinkType.Value] = defines.EnumUtils.enumFormat(this)
  }

  import Entity._
  import Ontology._
  import play.api.libs.functional.syntax._

  implicit val linkFormat: Format[LinkF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Link) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ LINK_TYPE).formatWithDefault(LinkType.Associative) and
    (__ \ DATA \ DESCRIPTION).formatNullable[String] and
    (__ \ DATA \ IS_PROMOTABLE).formatWithDefault(false) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).formatSeqOrEmpty[DatePeriodF]
  )(LinkF.apply _, unlift(LinkF.unapply))

  implicit object Converter extends Writable[LinkF] {
    lazy val restFormat: Format[LinkF] = linkFormat
  }
}

case class LinkF(
  isA: EntityType.Value = EntityType.Link,
  id: Option[String],
  linkType: LinkF.LinkType.Type,
  description: Option[String] = None,
  isPromotable: Boolean = false,
  @models.relation(Ontology.ENTITY_HAS_DATE)
  dates: Seq[DatePeriodF] = Nil
) extends Model with Persistable with Temporal


object Link {
  import Entity._
  import Ontology._
  import play.api.libs.functional.syntax._
  import defines.EnumUtils.enumMapping
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = models.UserProfile.UserProfileResource.restReads
  private implicit val accessPointReads = models.AccessPoint.Converter.restReads
  private implicit val systemEventReads = SystemEvent.SystemEventResource.restReads

  implicit val metaReads: Reads[Link] = (
    __.read[LinkF] and
    (__ \ RELATIONSHIPS \ LINK_HAS_TARGET).lazyReadSeqOrEmpty(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ LINK_HAS_LINKER).readHeadNullable[UserProfile] and
    (__ \ RELATIONSHIPS \ LINK_HAS_BODY).readSeqOrEmpty[AccessPoint] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PROMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ DEMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Link.apply _)

  implicit object LinkResource extends backend.ContentType[Link]  {
    val entityType = EntityType.Link
    val contentType = ContentTypes.Link
    val restReads: Reads[Link] = metaReads
  }

  import LinkF._

  val form = Form(mapping(
    Entity.ISA -> ignored(EntityType.Link),
    Entity.ID -> optional(nonEmptyText),
    LINK_TYPE -> default(enumMapping(LinkType), LinkType.Associative),
    DESCRIPTION -> optional(nonEmptyText), // TODO: Validate this server side
    Ontology.IS_PROMOTABLE -> default(boolean, false),
    DATES -> seq(DatePeriod.form.mapping)
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
  targets: Seq[AnyModel] = Nil,
  user: Option[UserProfile] = None,
  bodies: Seq[AccessPoint] = Nil,
  accessors: Seq[Accessor] = Nil,
  promoters: Seq[UserProfile] = Nil,
  demoters: Seq[UserProfile] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[LinkF]
  with Accessible
  with Promotable {
  def isPromotable: Boolean = model.isPromotable
  def opposingTarget(item: AnyModel): Option[AnyModel] = opposingTarget(item.id)
  def opposingTarget(itemId: String): Option[AnyModel] = targets.find(_.id != itemId)
}