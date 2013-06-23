package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.AccessibleEntity


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

  lazy implicit val countryReads: Reads[CountryF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Country)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String]
    )(CountryF.apply _)

  implicit val restFormat: Format[CountryF] = Format(countryReads,countryWrites)

  private implicit val systemEventReads = SystemEventFormat.metaReads
  implicit val metaReads: Reads[CountryMeta] = (
    __.read[JsObject] and
    __.read[CountryF] and
      // Latest event
      (__ \ RELATIONSHIPS \ AccessibleEntity.EVENT_REL).lazyRead[List[SystemEventMeta]](
        Reads.list[SystemEventMeta]).map(_.headOption)
    )(CountryMeta.apply _)

}