package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.{Accessible, Accessor}
import eu.ehri.project.definitions.Ontology


object GroupFormat {
  import models.GroupF._
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
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Group)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).read[String] and
      (__ \ DATA \ DESCRIPTION).readNullable[String]
    )(GroupF.apply _)

  implicit val restFormat: Format[GroupF] = Format(groupReads,groupWrites)
  private lazy implicit val systemEventReads = SystemEventFormat.metaReads

  implicit val metaReads: Reads[Group] = (
    __.read[GroupF] and
    (__ \ RELATIONSHIPS \ Accessor.BELONGS_REL).lazyReadNullable[List[Group]](
      Reads.list[Group]).map(_.getOrElse(List.empty[Group])) and
    (__ \ RELATIONSHIPS \ Ontology.IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ Ontology.ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
      Reads.list[SystemEvent]).map(_.flatMap(_.headOption))
  )(Group.apply _)
}