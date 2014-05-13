package models

import base._

import models.base.Persistable
import defines.EntityType
import models.json._
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._

object CountryF {

  val REPORT = "report"
  val ABSTRACT = "abstract"

  import Entity._

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
    (__ \ TYPE).readIfEquals(EntityType.Country) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ DATA \ ABSTRACT).readNullable[String] and
    (__ \ DATA \ REPORT).readNullable[String]
  )(CountryF.apply _)

  implicit val countryFormat: Format[CountryF] = Format(countryReads,countryWrites)

  implicit object Converter extends RestConvertable[CountryF] with ClientConvertable[CountryF] {
    lazy val restFormat = countryFormat
    lazy val clientFormat = Json.format[CountryF]
  }
}

case class CountryF(
  isA:EntityType.Value = EntityType.Country,
  id: Option[String],
  identifier: String,
  abs: Option[String],
  report: Option[String]
) extends Model with Persistable


object Country {
  import eu.ehri.project.definitions.Ontology._
  import Entity._
  import CountryF._

  implicit val metaReads: Reads[Country] = (
    __.read[CountryF](countryReads) and
    // Latest event
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).nullableListReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(Country.apply _)

  implicit object Converter extends ClientConvertable[Country] with RestReadable[Country] {
    val restReads = metaReads

    val clientFormat: Format[Country] = (
      __.format[CountryF](CountryF.Converter.clientFormat) and
      (__ \ "accessibleTo").nullableListFormat(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Country.apply _, unlift(Country.unapply _))
  }

  implicit object Resource extends RestResource[Country] {
    val entityType = EntityType.Country
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Country),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=2,maxLength=2), // ISO 2-letter field
      ABSTRACT -> optional(nonEmptyText),
      REPORT -> optional(nonEmptyText)
    )(CountryF.apply)(CountryF.unapply)
  )
}


// Stub
case class Country(
  model: CountryF,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[CountryF]
  with Accessible
  with Holder[Repository] {

  override def toStringLang(implicit lang: Lang) = views.Helpers.countryCodeToName(id)
}