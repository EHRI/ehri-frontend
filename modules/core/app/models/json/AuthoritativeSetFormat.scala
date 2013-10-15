package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import models.base.{Accessible, Accessor}
import eu.ehri.project.definitions.Ontology


object AuthoritativeSetFormat {
  import models.AuthoritativeSetF._
  import models.Entity._
  import Ontology._

  implicit val authoritativeSetWrites: Writes[AuthoritativeSetF] = new Writes[AuthoritativeSetF] {
    def writes(d: AuthoritativeSetF): JsValue = {
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

  implicit val authoritativeSetReads: Reads[AuthoritativeSetF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.AuthoritativeSet)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).readNullable[String] and
      (__ \ DATA \ DESCRIPTION).readNullable[String]
    )(AuthoritativeSetF.apply _)

  implicit val restFormat: Format[AuthoritativeSetF] = Format(authoritativeSetReads,authoritativeSetWrites)


  private implicit val systemEventReads = SystemEventFormat.metaReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[AuthoritativeSet] = (
    __.read[AuthoritativeSetF] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
      Reads.list[SystemEvent]).map(_.flatMap(_.headOption)) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(AuthoritativeSet.apply _)
}