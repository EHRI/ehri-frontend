package utils.search

import defines.EntityType

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

  def bestHitText: Seq[String] = (for {
    (hitField, hits) <- highlights
    (field, text) <- fields if field == hitField
    firstHit <- hits.headOption
  } yield text.replace(stripTags(firstHit), firstHit)).toSeq
}
