package utils.search

import defines.EntityType
import scala.annotation.tailrec
import scala._
import solr.SolrConstants._
import scala.Some

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
    = value.replaceAll("""<(?!\/?a(?=>|\s.*>))\/?.*?>""", "")

  def bestHitText: Seq[String] = {
    val handledFields = Seq(ID, ITEM_ID, NAME_EXACT, TYPE, DB_ID, NAME_MATCH)
    (for {
      (hitField, hits) <- highlights
      (field, text) <- fields if field == hitField && !handledFields.contains(field)
      firstHit <- hits.headOption
    } yield text.replace(stripTags(firstHit), firstHit)).toSeq
  }

  def highlight(text: String): String = {
    def canHighlightWith(raw: String, text: String): Option[String] = {
      val stripped = stripTags(raw)
      if (text.contains(stripped)) Some(text.replace(stripped, raw)) else None
    }
    @tailrec def tryHighlight(texts: List[String], input: String): String = texts match {
      case t :: rest => canHighlightWith(t, text) match {
        case rep@Some(replace) => tryHighlight(rest, replace)
        case None => tryHighlight(rest, input)
      }
      case Nil => input
    }
    tryHighlight(highlights.values.flatten.toList, text)
  }
}
