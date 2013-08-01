package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.{Accessible, Accessor}
import eu.ehri.project.definitions.Ontology


object CountryFormat {
  import models.Entity._
  import models.CountryF._

  implicit val countryWrites: Writes[CountryF] = new Writes[CountryF] {
    def writes(d: CountryF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          ABSTRACT -> d.abs,
          REPORT -> d.report
        )
      )
    }
  }

  lazy implicit val countryReads: Reads[CountryF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Country)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ ABSTRACT).readNullable[String] and
      (__ \ DATA \ REPORT).readNullable[String]
    )(CountryF.apply _)

  implicit val restFormat: Format[CountryF] = Format(countryReads,countryWrites)

  implicit val metaReads: Reads[Country] = (
    __.read[CountryF](countryReads) and
      // Latest event
    (__ \ RELATIONSHIPS \ Ontology.IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ Ontology.ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
      Reads.list(SystemEventFormat.metaReads)).map(_.flatMap(_.headOption))
    )(Country.apply _)

}