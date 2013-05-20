package views

import java.util.Locale

import views.html.helper.FieldConstructor
import models.base.{DescribedEntity, AccessibleEntity}
import play.api.mvc.Call
import play.api.i18n.Lang

import com.petebevin.markdown.MarkdownProcessor
import org.apache.commons.lang3.text.WordUtils
import org.apache.commons.lang3.StringUtils
import models.Entity


package object Helpers {

  // Pretty date/time handling
  import org.ocpsoft.pretty.time.PrettyTime
  def prettyDate(d: java.util.Date): String = {
    val p = new PrettyTime() // TODO: Locale awareness, but not sure how to handle this centrally yet
    p.format(d)
  }
  def prettyDate(d: org.joda.time.DateTime): String = prettyDate(d.toDate)
  def prettyDate(d: Option[org.joda.time.DateTime]): String = {
    d match {
      case Some(dt) => prettyDate(dt.toDate)
      case None => ""
    }
  }


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
   * @param text
   * @param max
   */
  def ellipsize(text: String, max: Int) = StringUtils.abbreviateMiddle(text, "...", max)

  /**
   * Get the URL for an unknown type of entity.
   */
  import defines.EntityType
  import controllers.routes
  import models.Entity

  def urlFor(a: AccessibleEntity) = urlForEntity(a.e)

  def urlForEntity(e: Entity): Call = e.isA match {
    case EntityType.SystemEvent => routes.SystemEvents.get(e.id)
    case EntityType.DocumentaryUnit => routes.DocumentaryUnits.get(e.id)
    case EntityType.HistoricalAgent => routes.HistoricalAgents.get(e.id)
    case EntityType.Repository => routes.Repositories.get(e.id)
    case EntityType.Group => routes.Groups.get(e.id)
    case EntityType.UserProfile => routes.UserProfiles.get(e.id)
    case EntityType.Annotation => routes.Annotations.get(e.id)
    case EntityType.Vocabulary => routes.Vocabularies.get(e.id)
    case EntityType.AuthoritativeSet => routes.AuthoritativeSets.get(e.id)
    case EntityType.Concept => routes.Concepts.get(e.id)
    case EntityType.ContentType => Call("GET", "#")
    case EntityType.Country => routes.Countries.get(e.id)
      // Anything we can't figure out, give an API ref
    case i => routes.ApiController.getAny(e.id)
  }

  /**
   * Get the display language of the given code in the current locale.
   * @param code
   * @param lang
   * @return
   */
  def displayLanguage(code: String)(implicit lang: Lang) = new java.util.Locale(code).getDisplayLanguage(lang.toLocale)

  /**
   * Get a list of code->name pairs for the given language.
   * @param lang
   * @return
   */
  def languagePairList(implicit lang: Lang): List[(String,String)] = {
    val locale = lang.toLocale
    java.util.Locale.getISOLanguages.map { code =>
      code -> WordUtils.capitalize(new java.util.Locale(code).getDisplayLanguage(locale))
    }.toList.sortBy(_._2)
  }

  /**
   * Get a list of ISO15924 script.
   *
   * NB: The implicit lang parameter is currently ignored because
   * the script data is not localised.
   * @param lang
   * @return
   */
  def scriptPairList(implicit lang: Lang): List[(String,String)] = {
    utils.Data.scripts.sortBy(_._2)
  }

  /**
   * Get a list of country->name pairs for the given language.
   * @param lang
   * @return
   */
  def countryPairList(implicit lang: Lang): List[(String,String)] = {
    val locale = lang.toLocale
    java.util.Locale.getISOCountries.map { code =>
      code -> WordUtils.capitalize(new java.util.Locale(locale.getLanguage, code).getDisplayCountry(locale))
    }.toList.sortBy(_._2)
  }

  /**
   * Get a language name for a given code.
   * @param code
   * @param lang
   * @return
   */
  def languageCodeToName(code: String)(implicit lang: Lang): String = {
    new Locale(code, "").getDisplayLanguage(lang.toLocale) match {
      case d if !d.isEmpty => d
      case _ => code
    }
  }

  /**
   * Get the script name for a given code.
   * @param code
   * @param lang
   * @return
   */
  def scriptCodeToName(code: String)(implicit lang: Lang): String = {
    var tmploc = new Locale.Builder().setScript(code).build()
    tmploc.getDisplayScript(lang.toLocale) match {
      case d if !d.isEmpty => d
      case _ => code
    }
  }

  /**
   * Get the country name for a given code.
   * @param code
   * @param lang
   * @return
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
   * @param field
   * @return
   */
  def fieldValues(field: play.api.data.Field): List[String] = {
    0.until(if (field.indexes.isEmpty) 0 else field.indexes.max + 1).flatMap(i => field("[" + i + "]").value).toList
  }
}
