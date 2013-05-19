package solr

import defines.EntityType

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
  val Score = Value("score.desc")
  val Name = Value("name_sort.asc")
  val DateNewest = Value("lastUpdated.desc")
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
) {

  /**
   * Set unset values from another (optional) instance.
   * @param default
   * @return
   */
  def setDefault(default: Option[SearchParams]): SearchParams = default match {
    case Some(d) => SearchParams(
      query = query orElse d.query,
      page = page orElse d.page,
      limit = limit orElse d.limit,
      sort = sort orElse d.sort,
      reverse = reverse orElse d.reverse,
      entities = if (entities.isEmpty) d.entities else entities,
      fields = fields orElse d.fields
    )
    case None => this
  }
}

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
