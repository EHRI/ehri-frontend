package views

import play.api.i18n.Lang
import org.apache.commons.lang3.StringUtils
import models._
import models.base.AnyModel
import play.api.mvc.Call
import scala.util.matching.Regex
import play.api.mvc.RequestHeader
import backend.Entity


package object Helpers {

  // Pretty relative date/time handling
  import org.ocpsoft.pretty.time.PrettyTime
  def relativeDate(d: java.util.Date)(implicit lang: Lang): String = {
    val p = new PrettyTime(lang.toLocale)
    p.format(d)
  }
  def relativeDate(d: org.joda.time.DateTime)(implicit lang: Lang): String = relativeDate(d.toDate)
  def relativeDate(d: Option[org.joda.time.DateTime])(implicit lang: Lang): String =
    d.fold("")(dt => relativeDate(dt.toDate))


  // Initialize Markdown processor for rendering markdown. NB: The
  // instance is apparently not thread safe, so using a threadlocal
  // here to be on the safe side.
  import org.pegdown.{LinkRenderer, Extensions, PegDownProcessor}
  import org.pegdown.ast.{AutoLinkNode, ExpLinkNode}
  import org.jsoup.Jsoup
  import org.jsoup.safety.Whitelist

  private val linkRenderer = new LinkRenderer() {
    override def render(node: AutoLinkNode) = {
      new LinkRenderer.Rendering(node.getText, node.getText)
        .withAttribute("rel", "nofollow")
        .withAttribute("target", "_blank")
        .withAttribute("class", "external")
    }
    override def render(node: ExpLinkNode, text: String) = {
      new LinkRenderer.Rendering(node.url, text)
        .withAttribute("rel", "nofollow")
        .withAttribute("target", "_blank")
        .withAttribute("class", "external")
        .withAttribute("title", node.title)
    }
  }
  private val markdownParser = new ThreadLocal[PegDownProcessor]
  private val whiteListStandard: Whitelist = Whitelist.basic()
    .addAttributes("a", "target", "_blank")
    .addAttributes("a", "class", "external")
    .addAttributes("a", "rel", "nofollow")
  private val whiteListStrict: Whitelist = Whitelist.simpleText().addTags("p", "a")
    .addAttributes("a", "target", "_blank")
    .addAttributes("a", "class", "external")
    .addAttributes("a", "rel", "nofollow")
  def getMarkdownProcessor = Option(markdownParser.get).getOrElse {
    val parser = new PegDownProcessor(Extensions.AUTOLINKS)
    markdownParser.set(parser)
    parser
  }

  def renderUntrustedMarkdown(text: String): String =
    Jsoup.clean(getMarkdownProcessor.markdownToHtml(text, linkRenderer), whiteListStrict)

  def renderMarkdown(text: String): String =
    Jsoup.clean(getMarkdownProcessor.markdownToHtml(text, linkRenderer), whiteListStandard)

  def renderTrustedMarkdown(text: String): String =
    getMarkdownProcessor.markdownToHtml(text, linkRenderer)

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
  def languagePairList(implicit lang: Lang): List[(String,String)] =
    utils.i18n.languagePairList(lang)

  /**
   * Get a list of ISO15924 script.
   *
   * NB: The implicit lang parameter is currently ignored because
   * the script data is not localised.
   */
  def scriptPairList(implicit lang: Lang): List[(String,String)] =
    utils.i18n.scriptPairList(lang)

  /**
   * Get a list of country->name pairs for the given language.
   */
  def countryPairList(implicit lang: Lang): List[(String,String)] =
    utils.i18n.countryPairList(lang)

  /**
   * Get a language name for a given code.
   */
  def languageCodeToName(code: String)(implicit lang: Lang): String =
    utils.i18n.languageCodeToName(code)(lang)

  /**
   * Get the script name for a given code.
   */
  def scriptCodeToName(code: String)(implicit lang: Lang): String =
    utils.i18n.scriptCodeToName(code)(lang)

  /**
   * Get the country name for a given code.
   */
  def countryCodeToName(code: String)(implicit lang: Lang): String =
    utils.i18n.countryCodeToName(code)(lang)

  /**
   * Function that shouldn't be necessary. Extract a list of values from
   * a repeated form field. There's probably a more correct way of handling this
   * but Play's multi value form support is so maddening it's difficult to figure
   * it out.
   */
  def fieldValues(field: play.api.data.Field): List[String] = {
    0.until(if (field.indexes.isEmpty) 0 else field.indexes.max + 1).flatMap(i => field("[" + i + "]").value).toList
  }

  def maybeActive(url: String)(implicit request: RequestHeader) = {
    if(request.path.equals(url)) "active" else ""
  }

  def maybeActivePath(url: String)(implicit request: RequestHeader) = {
    if(request.path.startsWith(url)) "active" else ""
  }

  def textDirection(d: models.base.Description) = if (d.isRightToLeft) "rtl" else "ltr"
}
