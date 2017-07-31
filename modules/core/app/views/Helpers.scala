package views

import defines.EntityType
import models.Entity
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.apache.commons.lang3.StringUtils
import play.api.i18n.Messages
import play.api.mvc.RequestHeader


object Helpers {

  // Pretty relative date/time handling
  import org.ocpsoft.prettytime.PrettyTime
  def relativeDate(d: java.util.Date)(implicit messages: Messages): String = {
    val p = new PrettyTime(messages.lang.toLocale)
    p.format(d)
  }

  def relativeDate(d: java.time.ZonedDateTime)(implicit messages: Messages): String =
    relativeDate(java.util.Date.from(d.toInstant))

  def relativeDate(d: Option[java.time.ZonedDateTime])(implicit messages: Messages): String =
    d.fold("")(d => relativeDate(d))

  /**
   * Get the field prefix for an entity type. This is just the entity type
   * string with a lower-cased first letter.
   *
   * As a general rule, do not use this because it is fragile.
   * FIXME: Better solution here.
   */
  def prefixFor(et: EntityType.Value): String = et match {
    case EntityType.VirtualUnit => prefixFor(EntityType.DocumentaryUnit)
    case _ => et.toString.substring(0, 1).toLowerCase + et.toString.substring(1)
  }


  def stripTags(htmlText: String): String = Jsoup.clean(htmlText, Whitelist.none())

  /**
   * Condense multiple descriptions that are next to each other in a list.
   * This is not the same as removing duplicates
   */
  def condenseMultipleDescriptions(items: Seq[Entity]): Seq[Entity] = {
    items.foldLeft(Seq[Entity]()) { case (s,d) =>
      s.lastOption.fold({
        s ++ Seq(d)
      })(ld =>
        if (ld.id == d.id) s else s ++ Seq(d))
    }
  }

  def argsWithDefaults(args: Seq[(Symbol,Any)], defaults: (Symbol, Any)*): Seq[(Symbol, Any)] =
    args ++ defaults.filterNot(v => args.exists(a => a._1 == v._1))

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
        List(1 to lp)
      // Close to start, so only hide later pages
      case lp if lp > 5 + window && page < 1 + window =>
        List(1 until (4 + window), (lp - 1) to lp)
      // Around the middle, hide both start and end pages
      case lp if lp - window > page && page > window =>
        List(1 to 2, (page - adjacents) to (page + adjacents), (lp - 1) to lp)
      // Close to end, hide beginning pages...
      case lp =>
        List(1 to 2, (lp - (2 + window)) to lp)
    }
  }

  /**
   * Function to truncate and add ellipses to long strings
   */
  def ellipsize(text: String, max: Int): String = StringUtils.abbreviate(stripTags(text), max)

  /**
   * Get a list of code->name pairs for the given language.
   */
  def languagePairList(implicit messages: Messages): List[(String,String)] =
    utils.i18n.languagePairList(messages)

  /**
   * Get a list of ISO15924 script.
   *
   * NB: The implicit lang parameter is currently ignored because
   * the script data is not localised.
   */
  def scriptPairList(implicit messages: Messages): List[(String,String)] =
    utils.i18n.scriptPairList(messages)

  /**
   * Get a list of country->name pairs for the given language.
   */
  def countryPairList(implicit messages: Messages): List[(String,String)] =
    utils.i18n.countryPairList(messages)

  /**
   * Get a language name for a given code.
   */
  def languageCodeToName(code: String)(implicit messages: Messages): String =
    utils.i18n.languageCodeToName(code)(messages)

  /**
   * Get the script name for a given code.
   */
  def scriptCodeToName(code: String)(implicit messages: Messages): String =
    utils.i18n.scriptCodeToName(code)(messages)

  /**
   * Get the country name for a given code.
   */
  def countryCodeToName(code: String)(implicit messages: Messages): String =
    utils.i18n.countryCodeToName(code)(messages)

  /**
   * Function that shouldn't be necessary. Extract a list of values from
   * a repeated form field. There's probably a more correct way of handling this
   * but Play's multi value form support is so maddening it's difficult to figure
   * it out.
   */
  def fieldValues(field: play.api.data.Field): List[String] = {
    0.until(if (field.indexes.isEmpty) 0 else field.indexes.max + 1).flatMap(i => field("[" + i + "]").value).toList
  }

  def maybeActive(url: String)(implicit request: RequestHeader): String = {
    if(request.path.equals(url)) "active" else ""
  }

  def maybeActivePath(url: String)(implicit request: RequestHeader): String = {
    if(request.path.startsWith(url)) "active" else ""
  }

  def textDirection(d: models.base.Description): String = if (d.isRightToLeft) "rtl" else "ltr"
}
