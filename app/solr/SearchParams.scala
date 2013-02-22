package solr

import defines.EntityType
import models.UserProfile

/**
 * Helper for pagination.
 */

object SearchField extends Enumeration {
  type Field = Value
  val All = Value("all")
  val Title = Value("title")
  val Creator = Value("creator")
  val StartDate = Value("start_date")
}

object SearchOrder extends Enumeration {
  type Order = Value
  val Relevance = Value("relevance")
  val Title = Value("title")
  val DateNewest = Value("dateNewest")
  val DateOldest = Value("dateOldest")
}

object SearchType extends Enumeration {
  type Type = Value
  val All = Value("all")
  val Collection = Value("collection")
  val Authority = Value("authority")
  val Repository = Value("repository")
}


/**
 * User: michaelb
 */
case class SearchParams(
  fields: List[String] = Nil,
  entity: Option[EntityType.Value] = None,
  query: Option[String] = None,
  page: Int = 1,
  offset: Int = 0,
  limit: Int = 20,
  sort: Option[SearchOrder.Value] = None,
  reversed: Boolean = false,
  userOpt: Option[models.UserProfile] = None,
  facets: Map[String, Seq[String]] = Map()
)
