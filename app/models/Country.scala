package models

import base._

import models.base.Persistable
import defines.EntityType
import models.json.{RestReadable, ClientConvertable, RestConvertable}
import play.api.i18n.Lang

object CountryF {
  implicit object Converter extends RestConvertable[CountryF] with ClientConvertable[CountryF] {
    lazy val restFormat = models.json.rest.countryFormat
    lazy val clientFormat = models.json.client.countryFormat
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
    val clientFormat = models.json.client.countryMetaFormat
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