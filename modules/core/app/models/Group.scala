package models

import models.base._
import defines.{ContentTypes, EntityType}
import play.api.libs.json._
import models.json._
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject
import eu.ehri.project.definitions.Ontology
import backend._
import play.api.libs.json.JsObject

object GroupF {

  val NAME = "name"
  val DESCRIPTION = "description"

  import Entity._

  implicit val groupWrites: Writes[GroupF] = new Writes[GroupF] {
    def writes(d: GroupF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          DESCRIPTION -> d.description
        )
      )
    }
  }

  implicit val groupReads: Reads[GroupF] = (
    (__ \ TYPE).readIfEquals(EntityType.Group) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ DATA \ NAME).read[String] and
    (__ \ DATA \ DESCRIPTION).readNullable[String]
  )(GroupF.apply _)

  implicit val groupFormat: Format[GroupF] = Format(groupReads,groupWrites)

  implicit object Converter extends Writable[GroupF] {
    lazy val restFormat = groupFormat
  }
}

case class GroupF(
  isA: EntityType.Value = EntityType.Group,
  id: Option[String],
  identifier: String,
  name: String,
  description: Option[String] = None
) extends Model with Persistable

object Group {
  import GroupF._
  import Entity._
  import Ontology._

  private lazy implicit val systemEventReads = SystemEvent.SystemEventResource.restReads

  implicit val metaReads: Reads[Group] = (
    __.read[GroupF] and
    (__ \ RELATIONSHIPS \ ACCESSOR_BELONGS_TO_GROUP).lazyNullableSeqReads(metaReads) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyNullableSeqReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Group.apply _)

  implicit object GroupResource extends backend.ContentType[Group]  {
    val entityType = EntityType.Group
    val contentType = ContentTypes.Group
    val restReads = metaReads
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Group),
      ID -> optional(text),
      IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      DESCRIPTION -> optional(nonEmptyText)
    )(GroupF.apply)(GroupF.unapply)
  )
}


case class Group(
  model: GroupF,
  groups: Seq[Group] = Nil,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends MetaModel[GroupF]
  with Accessor
  with Accessible {

  override def toStringLang(implicit messages: Messages) = model.name
}

