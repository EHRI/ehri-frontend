package models

import base._

import models.base.Persistable
import defines.EntityType
import models.json._
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.functional.syntax._

object CountryF {

  val REPORT = "report"
  val ABSTRACT = "abstract"

  implicit object Converter extends RestConvertable[CountryF] with ClientConvertable[CountryF] {
    lazy val restFormat = models.json.CountryFormat.restFormat
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
  implicit object Converter extends ClientConvertable[Country] with RestReadable[Country] {
    val restReads = models.json.CountryFormat.metaReads

    val clientFormat: Format[Country] = (
      __.format[CountryF](CountryF.Converter.clientFormat) and
      nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Country.apply _, unlift(Country.unapply _))
  }

  implicit object Resource extends RestResource[Country] {
    val entityType = EntityType.Country
  }
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