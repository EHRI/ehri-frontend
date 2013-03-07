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
  val Title = Value("name")
  val DateNewest = Value("lastUpdated")
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
  query: Option[String] = None,
  page: Option[Int] = Some(1),
  limit: Option[Int] = Some(SearchParams.DEFAULT_LIMIT),
  sort: Option[SearchOrder.Value] = None,
  reverse: Option[Boolean] = Some(false),
  entities: List[EntityType.Value] = Nil,
  fields: Option[List[String]] = None
)

object SearchParams {
  final val DEFAULT_LIMIT = 20
  final val REVERSE = "desc"
  final val SORT = "sort"
  final val LIMIT = "limit"
  final val PAGE = "page"
  final val QUERY = "q"
  final val FIELD = "qf"
  final val ENTITY = "st"

  import play.api.data.Forms._
  import play.api.data.Form

  val form = Form(
    mapping(
      QUERY -> optional(nonEmptyText),
      PAGE -> optional(number),
      LIMIT -> optional(number),
      SORT -> optional(models.forms.enum(SearchOrder)),
      REVERSE -> optional(boolean),
      ENTITY -> list(models.forms.enum(EntityType)),
      FIELD -> optional(list(nonEmptyText))
    )(SearchParams.apply _)(SearchParams.unapply _)
  )
}
