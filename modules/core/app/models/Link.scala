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
    val Associative = Value("associative")
    val Family = Value("family")
    val Hierarchical = Value("hierarchical")
    val Temporal = Value("temporal")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  import Entity._
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
        ),
        RELATIONSHIPS -> Json.obj(
          ENTITY_HAS_DATE -> Json.toJson(d.dates.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val linkReads: Reads[LinkF] = (
    (__ \ TYPE).readIfEquals(EntityType.Link) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ LINK_TYPE).readWithDefault(LinkType.Associative) and
    (__ \ DATA \ DESCRIPTION).readNullable[String] and
    (__ \ DATA \ IS_PROMOTABLE).readWithDefault(false) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).nullableListReads[DatePeriodF]
  )(LinkF.apply _)

  implicit object Converter extends BackendWriteable[LinkF] {
    lazy val restFormat = Format(linkReads,linkWrites)
  }
}

case class LinkF(
  isA: EntityType.Value = EntityType.Link,
  id: Option[String],
  linkType: LinkF.LinkType.Type,
  description: Option[String],
  isPromotable: Boolean = false,
  @models.relation(Ontology.ENTITY_HAS_DATE)
  dates: List[DatePeriodF] = Nil
) extends Model with Persistable with Temporal


object Link {
  import Entity._
  import Ontology._
  import play.api.libs.functional.syntax._
  import defines.EnumUtils.enumMapping
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = models.UserProfile.Resource.restReads
  private implicit val accessPointReads = models.AccessPoint.Converter.restReads
  private implicit val systemEventReads = SystemEvent.Resource.restReads

  implicit val metaReads: Reads[Link] = (
    __.read[LinkF] and
    (__ \ RELATIONSHIPS \ LINK_HAS_TARGET).lazyNullableListReads(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ LINK_HAS_LINKER).nullableHeadReads[UserProfile] and
    (__ \ RELATIONSHIPS \ LINK_HAS_BODY).nullableListReads[AccessPointF] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyNullableListReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PROMOTED_BY).nullableListReads[UserProfile] and
    (__ \ RELATIONSHIPS \ DEMOTED_BY).nullableListReads[UserProfile] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Link.apply _)

  implicit object Resource extends BackendContentType[Link] {
    val entityType = EntityType.Link
    val contentType = ContentTypes.Link
    val restReads = metaReads
  }

  import LinkF._

  val form = Form(mapping(
    Entity.ISA -> ignored(EntityType.Link),
    Entity.ID -> optional(nonEmptyText),
    LINK_TYPE -> default(enumMapping(LinkType), LinkType.Associative),
    DESCRIPTION -> optional(nonEmptyText), // TODO: Validate this server side
    Ontology.IS_PROMOTABLE -> default(boolean, false),
    DATES -> list(DatePeriod.form.mapping)
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
  promoters: List[UserProfile] = Nil,
  demoters: List[UserProfile] = Nil,
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