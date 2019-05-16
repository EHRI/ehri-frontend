package views

import java.net.{MalformedURLException, URL}

import defines.{EntityType, PermissionType}
import models.base.Model
import models.{Annotation, Entity, UserProfile}
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import play.api.i18n.Messages
import play.api.mvc.{Call, RequestHeader}


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

  def argsWithConfig(fieldName: String, args: Seq[(Symbol,Any)])(implicit config: Option[forms.FormConfig], messages: Messages): Seq[(Symbol,Any)] = {
    (args
      ++ config.flatMap(_.hint(fieldName)).map('_hint -> _).toSeq
      ++ config.flatMap(_.required(fieldName)).map(_ => '_help -> Messages("constraint.required")))
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

  /**
    * Attempt to detect the direction of a piece of text, given
    * a context which assumes it is left-to-right.
    *
    * @param s the string
    * @param rtlContext if the context is right-to-left
    * @return true if the text is right-to-left
    */
  def isRightToLeft(s: String, rtlContext: Boolean = false): Boolean = {
    import com.ibm.icu.text.Bidi
    if (s.trim.isEmpty) rtlContext
    else Bidi.getBaseDirection(s) == Bidi.RTL
  }

  /**
    * The value of the HTML5 dir attribute based on the text.
    *
    * @param s the text string
    * @param rtl if the context is right-to-left
    * @return either "rtl" or "auto"
    */
  def textDirectionAttr(s: String, rtl: Boolean = false): String = if(isRightToLeft(s, rtl)) "rtl" else "auto"

  /**
    * Sort a set of annotations into three types.
    * @param annotations A list of annotations
    * @param userOpt An optional user context
    * @return A tuple of annotation sequences: the current user's, promoted, and other
    */
  def sortAnnotations(annotations: Seq[models.Annotation])(
    implicit userOpt: Option[UserProfile]): (Seq[Annotation], Seq[Annotation], Seq[Annotation]) = {
    val (mine,others) = annotations.filterNot(_.isPromoted).partition(_.isOwnedBy(userOpt))
    val promoted = annotations.filter(_.isPromoted)
    (mine, promoted, others)
  }

  def normalizeUrl(s: String): String = {
    try {
      new URL(s).toString
    } catch {
      case e: MalformedURLException if e.getMessage.startsWith("no protocol") => "http://" + s
      case _: MalformedURLException => s
    }
  }

  def isAnnotatable(item: Model, userOpt: Option[models.UserProfile]): Boolean = userOpt.exists { user =>
    item.contentType.exists {
      ct => user.hasPermission(ct, PermissionType.Annotate)
    }
  }

  def linkTo(item: Model): Call = linkTo(item.isA, item.id)

  def linkTo(isA: EntityType.Value, id: String): Call = isA match {
    case EntityType.Country => controllers.portal.routes.Countries.browse(id)
    case EntityType.Concept => controllers.portal.routes.Concepts.browse(id)
    case EntityType.DocumentaryUnit => controllers.portal.routes.DocumentaryUnits.browse(id)
    case EntityType.Repository => controllers.portal.routes.Repositories.browse(id)
    case EntityType.HistoricalAgent => controllers.portal.routes.HistoricalAgents.browse(id)
    case EntityType.UserProfile => controllers.portal.social.routes.Social.userProfile(id)
    case EntityType.Group => controllers.portal.routes.Groups.browse(id)
    case EntityType.Link => controllers.portal.routes.Links.browse(id)
    case EntityType.Annotation => controllers.portal.annotate.routes.Annotations.browse(id)
    case EntityType.Vocabulary => controllers.portal.routes.Vocabularies.browse(id)
    case EntityType.AuthoritativeSet => controllers.portal.routes.AuthoritativeSets.browse(id)
    case EntityType.VirtualUnit => controllers.portal.routes.VirtualUnits.browseVirtualCollection(id)
    case _ => Call("GET", "#")
  }

  /**
    * Fetch a gravitar URL for the user, defaulting to the stock picture.
    */
  def gravitar(img: Option[String]): String =
    img.map(_.replaceFirst("https?://", "//"))
      .getOrElse(controllers.portal.routes.PortalAssets.at("img/default-gravitar.png").url)

  def remoteGravitar(userId: String): String = {
    val hash = DigestUtils.md5Hex(s"$userId@ehri-project.eu")
    s"https://secure.gravatar.com/avatar/$hash?d=identicon"
  }


  def virtualUnitUrl(path: Seq[Model], id: String): Call = {
    if (path.isEmpty) controllers.portal.routes.VirtualUnits.browseVirtualCollection(id)
    else controllers.portal.routes.VirtualUnits.browseVirtualUnit(path.map(_.id).mkString(","), id)
  }

  def virtualUnitSearchUrl(path: Seq[Model], id: String): Call = {
    if (path.isEmpty) controllers.portal.routes.VirtualUnits.searchVirtualCollection(id)
    else controllers.portal.routes.VirtualUnits.searchVirtualUnit(path.map(_.id).mkString(","), id)
  }
}
