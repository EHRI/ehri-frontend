package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._


object ConceptDescriptionFormat {
  import defines.EnumWriter.enumWrites
  import Entity._
  import ConceptF._

  implicit val conceptDescriptionWrites = new Writes[ConceptDescriptionF] {
    def writes(d: ConceptDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          LANGUAGE -> d.languageCode,
          PREFLABEL -> d.prefLabel,
          ALTLABEL -> d.altLabels,
          DEFINITION -> d.definition,
          SCOPENOTE -> d.scopeNote
        )
      )
    }
  }

  import ConceptDescriptionF._
  implicit val conceptDescriptionReads: Reads[ConceptDescriptionF] = (
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ LANGUAGE).read[String] and
      (__ \ DATA \ PREFLABEL).read[String] and
      (__ \ DATA \ ALTLABEL).readNullable[List[String]] and
      (__ \ DATA \ DEFINITION).readNullable[String] and
      (__ \ DATA \ SCOPENOTE).readNullable[String]
  )(ConceptDescriptionF.apply _)

  implicit val conceptDescriptionFormat: Format[ConceptDescriptionF] = Format(conceptDescriptionReads,conceptDescriptionWrites)
}