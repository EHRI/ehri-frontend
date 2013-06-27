package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.{Accessible, Accessor}


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

  implicit val metaReads: Reads[CountryMeta] = (
    __.read[CountryF](countryReads) and
      // Latest event
    (__ \ RELATIONSHIPS \ Accessible.REL).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ Accessible.EVENT_REL).lazyReadNullable[List[SystemEventMeta]](
      Reads.list(SystemEventFormat.metaReads)).map(_.flatMap(_.headOption))
    )(CountryMeta.apply _)

}