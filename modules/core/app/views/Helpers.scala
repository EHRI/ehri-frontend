package views

import java.util.Locale

import views.html.helper.FieldConstructor
import models.base.AnyModel
import play.api.i18n.Lang

import com.petebevin.markdown.MarkdownProcessor
import org.apache.commons.lang3.text.WordUtils
import org.apache.commons.lang3.StringUtils
import models._
import play.api.mvc.Call
import defines.EntityType


package object Helpers {

  // Pretty relative date/time handling
  import org.ocpsoft.pretty.time.PrettyTime
  def relativeDate(d: java.util.Date)(implicit lang: Lang): String = {
    val p = new PrettyTime(lang.toLocale)
    p.format(d)
  }
  def relativeDate(d: org.joda.time.DateTime)(implicit lang: Lang): String = relativeDate(d.toDate)
  def relativeDate(d: Option[org.joda.time.DateTime])(implicit lang: Lang): String
      = d.map(dt => relativeDate(dt.toDate)) getOrElse ""


  // Initialize Markdown processor for rendering markdown
  private val markdownProcessor = new MarkdownProcessor

  def renderMarkdown(text: String) = markdownProcessor.markdown(text)

  /**
   * Condense multiple descriptions that are next to each other in a list.
   * This is not the same as removing duplicates
   */
  def condenseMultipleDescriptions(items: Seq[Entity]): Seq[Entity] = {
    items.foldLeft(Seq[Entity]()) { case (s,d) =>
      s.lastOption.map { ld =>
        if (ld.id == d.id) s else s ++ Seq(d)
      } getOrElse {
        s ++ Seq(d)
      }
    }
  }

  /*
   * Helper to provide Digg-style pagination, like:
   *    1, 2 ... 18, 19, 20, 21, 22 ... 55, 56
   * Logic borrowed from here:
   *   http://www.strangerstudios.com/sandbox/pagination/diggstyle_code.txt
   */
  def paginationRanges(page: Int, lastPage: Int, adjacents: Int = 3): List[Range] = {
    val window = adjacents * 2
    lastPage match {
      // Last page is the same as single page... no ranges
      case lp if lp <= 1 => Nil
      // Not enough pages to bother hiding any...
      case lp if lp < 7 + window =>  
        List((1 to lp))
      // Close to start, so only hide later pages
      case lp if lp > 5 + window && page < 1 + window =>
        List(1 until (4 + window), ((lp - 1) to lp))  
      // Around the middle, hide both start and end pages
      case lp if lp - window > page && page > window =>
        List((1 to 2), ((page - adjacents) to (page + adjacents)), ((lp - 1) to lp))
      // Close to end, hide beginning pages...
      case lp =>
        List((1 to 2), ((lp - (2 + window)) to lp))
    }
  }

  /**
   * Function to truncate and add ellipses to long strings
   */
  def ellipsize(text: String, max: Int) = StringUtils.abbreviateMiddle(text, "...", max)

  /**
   * Get the display language of the given code in the current locale.
   */
  def displayLanguage(code: String)(implicit lang: Lang) = new java.util.Locale(code).getDisplayLanguage(lang.toLocale)

  /**
   * Get a list of code->name pairs for the given language.
   */
  def languagePairList(implicit lang: Lang): List[(String,String)] = {
    val locale = lang.toLocale
    lang3to2lookup.map { case (c3,c2) =>
      c3 -> WordUtils.capitalize(new java.util.Locale(c2).getDisplayLanguage(locale))
    }.toList.sortBy(_._2)
  }

  /**
   * Get a list of ISO15924 script.
   *
   * NB: The implicit lang parameter is currently ignored because
   * the script data is not localised.
   */
  def scriptPairList(implicit lang: Lang): List[(String,String)] = {
    utils.Data.scripts.sortBy(_._2)
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

  /**
   * Lazily build a lookup of ISO 639-2 (3-letter) to 639-1 (2-letter) codes
   */
  private lazy val lang3to2lookup: Map[String,String] = Locale.getISOLanguages.flatMap { code =>
    new Locale(code, "").getISO3Language match {
      case c3 if c3 != "" => Some(c3 -> code)
      case _ => Nil
    }
  }.toMap

  /**
   * Get the name for a language, if we can find one.
   */
  private def languageCode2ToNameOpt(code: String)(implicit lang: Lang): Option[String] = {
    new Locale(code, "").getDisplayLanguage(lang.toLocale) match {
      case d if !d.isEmpty => Some(d)
      case _ => None
    }
  }

  /**
   * Get a language name for a given code.
   */
  def languageCodeToName(code: String)(implicit lang: Lang): String = {
    if (code.size == 2) {
      languageCode2ToNameOpt(code).getOrElse(code)
    } else {
      lang3to2lookup.get(code).flatMap(c2 => languageCode2ToNameOpt(c2)).getOrElse(code)
    }
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

  /**
   * Get the country name for a given code.
   */
  def countryCodeToName(code: String)(implicit lang: Lang): String = {
    new Locale("", code).getDisplayCountry(lang.toLocale) match {
      case d if !d.isEmpty => d
      case _ => code
    }
  }

  /**
   * Function that shouldn't be necessary. Extract a list of values from
   * a repeated form field. There's probably a more correct way of handling this
   * but Play's multi value form support is so maddening it's difficult to figure
   * it out.
   */
  def fieldValues(field: play.api.data.Field): List[String] = {
    0.until(if (field.indexes.isEmpty) 0 else field.indexes.max + 1).flatMap(i => field("[" + i + "]").value).toList
  }
}
