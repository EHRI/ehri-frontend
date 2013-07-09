package models

import base._

import models.base.Persistable
import defines.EntityType
import models.json._
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.functional.syntax._

object CountryF {
  implicit object Converter extends RestConvertable[CountryF] with ClientConvertable[CountryF] {
    lazy val restFormat = models.json.CountryFormat.restFormat
    lazy val clientFormat = Json.format[CountryF]
  }
}

case class CountryF(
  isA:EntityType.Value = EntityType.Country,
  id: Option[String],
  identifier: String
) extends Model with Persistable


object Country {
  final val REPOSITORY_REL = "hasCountry"
}

object CountryMeta {
  implicit object Converter extends ClientConvertable[CountryMeta] with RestReadable[CountryMeta] {
    val restReads = models.json.CountryFormat.metaReads

    val clientFormat: Format[CountryMeta] = (
      __.format[CountryF](CountryF.Converter.clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEventMeta](SystemEventMeta.Converter.clientFormat)
      )(CountryMeta.apply _, unlift(CountryMeta.unapply _))
  }
}


// Stub
case class CountryMeta(
  model: CountryF,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta] = None
) extends AnyModel
  with MetaModel[CountryF]
  with Accessible {

  override def toStringLang(implicit lang: Lang) = views.Helpers.countryCodeToName(id)
}