package models

import models.base._
import defines.EntityType
import play.api.libs.json._
import models.json._
import play.api.i18n.Lang
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject
import eu.ehri.project.definitions.Ontology

object GroupF {

  val NAME = "name"
  val DESCRIPTION = "description"

  import models.Entity._

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

  implicit object Converter extends RestConvertable[GroupF] with ClientConvertable[GroupF] {
    lazy val restFormat = groupFormat
    lazy val clientFormat = Json.format[GroupF]
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

  private lazy implicit val systemEventReads = SystemEvent.Converter.restReads

  implicit val metaReads: Reads[Group] = (
    __.read[GroupF] and
      (__ \ RELATIONSHIPS \ ACCESSOR_BELONGS_TO_GROUP).lazyReadNullable[List[Group]](
        Reads.list[Group]).map(_.getOrElse(List.empty[Group])) and
      (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
        Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
      (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
        Reads.list[SystemEvent]).map(_.flatMap(_.headOption)) and
      (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
    )(Group.apply _)

  implicit object Converter extends ClientConvertable[Group] with RestReadable[Group] {
    val restReads = metaReads

    val clientFormat: Format[Group] = (
      __.format[GroupF](GroupF.Converter.clientFormat) and
      (__ \ "groups").lazyNullableListFormat(clientFormat) and
      (__ \ "accessibleTo").lazyNullableListFormat(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Group.apply _, unlift(Group.unapply _))
  }

  implicit object Resource extends RestResource[Group] {
    val entityType = EntityType.Group
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
  groups: List[Group] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends MetaModel[GroupF]
  with Accessor
  with Accessible {

  override def toStringLang(implicit lang: Lang) = model.name
}

