package utils.search

import defines.EntityType
import scala.annotation.tailrec
import scala._
import solr.SolrConstants._
import play.api.templates.Html

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
    def canHighlightWith(raw: String, text: Html): Option[Html] = {
      val stripped = stripTags(raw)
      if (text.body.contains(stripped)) Some(Html(text.body.replace(stripped, raw))) else None
    }
    @tailrec def tryHighlight(texts: Seq[String], input: Html, ok: Boolean): (Html, Boolean) = texts match {
      case t :: rest => canHighlightWith(t, text) match {
        case rep@Some(replace) => tryHighlight(rest, replace, ok = true)
        case None => tryHighlight(rest, input, ok)
      }
      case Nil => (input,ok)
    }
    tryHighlight(highlightFields, text, ok = false)
  }
}
