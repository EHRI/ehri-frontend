package models

import base._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.{Format, Json}
import defines.EnumUtils.enumWrites
import play.api.i18n.Lang
import java.util.Locale
import models.json.{ClientConvertable, RestConvertable}

object CountryF {
  lazy implicit val countryFormat: Format[CountryF] = json.CountryFormat.restFormat


  implicit object Converter extends RestConvertable[CountryF] with ClientConvertable[CountryF] {
    lazy val restFormat = models.json.rest.countryFormat
    lazy val clientFormat = models.json.client.countryFormat
  }
}

case class CountryF(
  val id: Option[String],
  val identifier: String
) extends Persistable {
  val isA = EntityType.Country
}


object Country {
  final val REPOSITORY_REL = "hasCountry"
}

case class Country(e: Entity)
  extends AccessibleEntity
  with AnnotatableEntity
  with Formable[CountryF] {

  lazy val formable: CountryF = Json.toJson(e).as[CountryF]
  lazy val formableOpt: Option[CountryF] = Json.toJson(e).asOpt[CountryF]

  override def toString = toStringLang(Lang.defaultLang)

  /**
   * Show the name language aware.
   * @param lang
   * @return
   */
  override def toStringLang(implicit lang: Lang) = new Locale("", id).getDisplayCountry(lang.toLocale) match {
    case d if !d.isEmpty => d
    case _ => id
  }
}

// Stub
case class CountryMeta(
  model: CountryF,
  latestEvent: Option[SystemEventMeta] = None
)