package utils.search

import defines.EntityType
import scala.annotation.tailrec
import scala._
import solr.SolrConstants._
import play.twirl.api.{HtmlFormat, Html}

/**
 * User: michaelb
 */
case class SearchHit(
  id: String,
  itemId: String,
  name: String,
  `type`: EntityType.Value,
  gid: Long,
  fields: Map[String,String] = Map.empty,
  highlights: Map[String,Seq[String]] = Map.empty,
  phrases: Seq[String] = Seq.empty
) {

  private def stripTags(value: String): String
    = value.replaceAll("""<(?!\/?(?=>|\s.*>))\/?.*?>""", "")

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
    k == ID || k == ITEM_ID || k == DB_ID || k == TYPE
  }

  private lazy val highlightFields = filteredHighlights.values.flatten.toList

  def bestHitText: Seq[String] = {
    val handledFields = Seq(ID, ITEM_ID, DB_ID, NAME_EXACT, TYPE, NAME_MATCH)
    (for {
      (hitField, hits) <- filteredHighlights
      (field, text) <- fields if field == hitField && !handledFields.contains(field)
      firstHit <- hits.headOption
    } yield text.replace(stripTags(firstHit), firstHit)).toSeq
  }

  /**
   * Try and highlight some text according to the highlights.
   * @param text Some input text
   * @return  The text and a boolean indicating if highlighting was successful
   */
  def highlight(text: Html): (Html, Boolean) = {
    def canHighlightWith(raw: String, html: Html): Either[Html,Html] = {
      // NB: We have to escape the input string using HtmlFormat because it's
      // specialised to avoid XSS. This assumes that the highlight material coming
      // back from Solr doesn't itself contain XSS attacks... what are the chances
      // this will come back to bite me?
      val stripped = HtmlFormat.escape(stripTags(raw)).body
      if (html.body.contains(stripped)) Right(Html(html.body.replace(stripped, raw)))
      else Left(html)
    }
    @tailrec def tryHighlight(texts: Seq[String], input: Html, ok: Boolean): (Html, Boolean) = {
      texts match {
        case t :: rest => canHighlightWith(t.trim, input) match {
          case Right(replace) => tryHighlight(rest, replace, ok = true)
          case Left(last) => tryHighlight(rest, last, ok)
        }
        case Nil => (input,ok)
      }
    }
    tryHighlight(highlightFields, text, ok = false)
  }
}
