package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._


object CountryFormat {
  import models.Entity._

  implicit val countryWrites: Writes[CountryF] = new Writes[CountryF] {
    def writes(d: CountryF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier
        )
      )
    }
  }

  implicit val countryReads: Reads[CountryF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Country)) andKeep
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String]
    )(CountryF.apply _)

  implicit val countryFormat: Format[CountryF] = Format(countryReads,countryWrites)
}