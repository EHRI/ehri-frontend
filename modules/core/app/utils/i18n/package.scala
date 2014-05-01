package utils

import play.api.i18n.{Lang, Messages}
import java.util.Locale

package object i18n {
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
      case d if !d.isEmpty => Some(d)
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
      .getOrElse(Messages("languageCode." + c))
  }

  /**
   * Get the script name for a given code. This doesn't work with Java 6 so we have to sacrifice
   * localised script names. On Java 7 we'd do:
   *
   * var tmploc = new Locale.Builder().setScript(code).build()
   *   tmploc.getDisplayScript(lang.toLocale) match {
   *   case d if !d.isEmpty => d
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
}
