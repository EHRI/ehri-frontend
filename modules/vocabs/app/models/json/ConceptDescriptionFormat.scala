package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._
import defines.EntityType
import defines.EnumUtils._
import models.base.Description
import eu.ehri.project.definitions.Ontology


object ConceptDescriptionFormat {
  import Entity._
  import ConceptF._
  import AccessPointFormat._

  implicit val conceptDescriptionWrites = new Writes[ConceptDescriptionF] {
    def writes(d: ConceptDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          LANGUAGE -> d.languageCode,
          PREFLABEL -> d.name,
          ALTLABEL -> d.altLabels,
          DEFINITION -> d.definition,
          SCOPENOTE -> d.scopeNote
        ),
        RELATIONSHIPS -> Json.obj(
          Ontology.HAS_ACCESS_POINT -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq),
          Ontology.HAS_UNKNOWN_PROPERTY -> Json.toJson(d.unknownProperties.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  import ConceptDescriptionF._
  implicit val conceptDescriptionReads: Reads[ConceptDescriptionF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.ConceptDescription)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ LANGUAGE).read[String] and
      (__ \ DATA \ PREFLABEL).read[String] and
      (__ \ DATA \ ALTLABEL).readNullable[List[String]] and
      (__ \ DATA \ DEFINITION).readNullable[List[String]] and
      (__ \ DATA \ SCOPENOTE).readNullable[List[String]] and
      (__ \ RELATIONSHIPS \ Ontology.HAS_ACCESS_POINT).lazyReadNullable(
        Reads.list[AccessPointF]).map(_.getOrElse(List.empty[AccessPointF])) and
      (__ \ RELATIONSHIPS \ Ontology.HAS_UNKNOWN_PROPERTY)
        .lazyReadNullable(Reads.list[Entity]).map(_.getOrElse(List.empty[Entity]))
    )(ConceptDescriptionF.apply _)

  implicit val restFormat: Format[ConceptDescriptionF] = Format(conceptDescriptionReads,conceptDescriptionWrites)
}