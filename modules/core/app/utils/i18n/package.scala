package utils

import play.api.i18n.{Lang, Messages}
import java.util.Locale
import org.apache.commons.lang3.text.WordUtils

package object i18n {

  val defaultLang: String = Locale.getDefault.getISO3Language

  def languagePairList(implicit lang: Lang): List[(String,String)] = {
    val locale = lang.toLocale
    val localeLangs = utils.i18n.lang3to2lookup.map { case (c3,c2) =>
      c3 -> WordUtils.capitalize(new java.util.Locale(c2).getDisplayLanguage(locale))
    }.toList

    (localeLangs ::: utils.Data
      .additionalLanguages.map(l => l -> Messages("languageCode." + l))).sortBy(_._2)
  }

  /**
   * Lazily build a lookup of ISO 639-2 (3-letter) to 639-1 (2-letter) codes
   */
  lazy val lang3to2lookup: Map[String,String] = Locale.getISOLanguages.flatMap { code =>
    new Locale(code, "").getISO3Language match {
      case c3 if c3 != "" => Some(c3 -> code)
      case _ => Nil
    }
  }.toMap

  /**
   * Lazily build a lookup of ISO 639-2 (3-letter) to 639-1 (2-letter) codes
   */
  lazy val lang2to3lookup: Map[String,String] = Locale.getISOLanguages.flatMap { code =>
    new Locale(code, "").getISO3Language match {
      case c3 if c3 != "" => Some(code, c3)
      case _ => Nil
    }
  }.toMap

  /**
   * Get the name for a language, if we can find one.
   */
  def languageCode2ToNameOpt(code: String)(implicit lang: Lang): Option[String] = {
    new Locale(code, "").getDisplayLanguage(lang.toLocale) match {
      case d if d.nonEmpty => Some(d)
      case _ => None
    }
  }

  /**
   * Get a language name for a given code.
   */
  def languageCodeToName(code: String)(implicit lang: Lang): String = code match {
    case c if c == "mul" => Messages("languageCode.mul")
    case c if c.length == 2 => languageCode2ToNameOpt(code).getOrElse(code)
    case c =>lang3to2lookup.get(c)
      .flatMap(c2 => languageCode2ToNameOpt(c2))
      .getOrElse {
      val key = "languageCode." + c
      val i18n = Messages(key)
      if (i18n != key) i18n else c
    }
  }

  def scriptPairList(lang: Lang) = utils.Data.scripts.sortBy(_._2)

  /**
   * Get the script name for a given code. This doesn't work with Java 6 so we have to sacrifice
   * localised script names. On Java 7 we'd do:
   *
   * var tmploc = new Locale.Builder().setScript(code).build()
   *   tmploc.getDisplayScript(lang.toLocale) match {
   *   case d if d.nonEmpty => d
   *   case _ => code
   * }
   */
  def scriptCodeToName(code: String)(implicit lang: Lang): String = {
    try {
      // NB: Current ignores lang...
      utils.Data.scripts.toMap.getOrElse(code, code)
    } catch {
      // This should be an IllformedLocaleException
      // but we need to work with Java 6
      case _: Exception => code
    }
  }

  /**
   * Convert a country code to a name, first trying
   * the localised JDK locale names, and falling back
   * on our i18n messages file.
   */
  def countryCodeToName(code: String)(implicit lang: Lang): String = {
    new Locale("", code).getDisplayCountry(lang.toLocale) match {
      case d if d.nonEmpty && !d.equalsIgnoreCase(code) => d
      case c =>
        val key = "countryCode." + c.toLowerCase
        val i18n = Messages(key)
        if (i18n != key) i18n else c
    }
  }

  /**
   * Get a list of country->name pairs for the given language.
   */
  def countryPairList(implicit lang: Lang): List[(String,String)] = {
    val locale = lang.toLocale
    java.util.Locale.getISOCountries.map { code =>
      code -> WordUtils.capitalize(new java.util.Locale(locale.getLanguage, code).getDisplayCountry(locale))
    }.toList.sortBy(_._2)
  }
}
