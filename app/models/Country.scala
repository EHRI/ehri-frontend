package models

import base._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumUtils.enumWrites
import play.api.i18n.Lang
import java.util.Locale

object CountryF {
  lazy implicit val countryFormat = json.CountryFormat.countryFormat
}

case class CountryF(
  val id: Option[String],
  val identifier: String
) extends Persistable {
  val isA = EntityType.Country

  def toJson = Json.toJson(this)
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
