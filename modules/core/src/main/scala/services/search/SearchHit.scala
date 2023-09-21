package services.search

import scala.annotation.tailrec
import SearchConstants._
import models.EntityType
import play.api.libs.json.JsValue
import play.twirl.api.{Html,HtmlFormat}
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

/**
 * Class representing a search engine hit
 */
case class SearchHit(
  id: String,
  itemId: String,
  `type`: EntityType.Value,
  gid: Long,
  fields: Map[String,JsValue] = Map.empty,
  highlights: Map[String,Seq[String]] = Map.empty,
  phrases: Seq[String] = Seq.empty
) extends views.Highlighter {

  private def stripTags(value: String): String =
    value.replaceAll("""<(?!\/?(?=>|\s.*>))\/?.*?>""", "")

  private val skipFields = Set(
    ID, ITEM_ID, DB_ID, PARENT_ID, TYPE, LANGUAGE_CODE
  )

  /**
   * Filter the highlight fields for those that are used primarily for
   * programmatic purposes. This is to prevent us highlighting things
   * like IDs in the rare occasions when they are used as search queries.
   *
   * FIXME: This whole highlighting stuff is too complex and fragile since
   * we are trying to highlight ad-hoc HTML. There's just too much stuff that
   * can/will go wrong.
   */
  private lazy val filteredHighlights = highlights.filterNot { case (k, _) =>
    skipFields.contains(k)
  }

  private lazy val highlightFields: List[String] = filteredHighlights.values.flatten.toList

  /**
   * Try and highlight some text according to the highlights. The input text is
    * assumed to already contain HTML.
    *
   * @param text Some input text
   * @return  The text, with highlights inserted
   */
  def highlight(text: String): String = {
    def canHighlightWith(rawHighlight: String, text: String): Either[String,String] = {
      // NB: We have to escape the input string using HtmlFormat because it's
      // specialised to avoid XSS. This assumes that the highlight material coming
      // back from Solr doesn't itself contain XSS attacks... what are the chances
      // this will come back to bite me?
      val stripped = HtmlFormat.escape(stripTags(rawHighlight)).body
      if (text.contains(stripped)) Right(text.replace(stripped, rawHighlight))
      else Left(text)
    }
    @tailrec def tryHighlight(texts: Seq[String], input: String, ok: Boolean): String = {
      texts match {
        case t :: rest => canHighlightWith(t.trim, input) match {
          case Right(replace) => replace
          case Left(last) => tryHighlight(rest, last, ok)
        }
        case Nil => input
      }
    }

    tryHighlight(highlightFields, text, ok = false)
  }

  /**
    * Try and highlight some text which we know should not contain HTML.
    *
    * @param text Some input text
    * @return The text, with highlights inserted
    */
  def highlightText(text: String): Html = Html(highlight(Jsoup.clean(text, Whitelist.none())))
}
