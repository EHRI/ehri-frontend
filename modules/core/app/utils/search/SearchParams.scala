package utils.search

import defines.EntityType
import models.json.ClientConvertable
import play.api.libs.json.{Json, Format}
import backend.rest.Constants._


object SearchField extends Enumeration {
  type Field = Value
  val All = Value("all")
  val Title = Value("title")
  val Creator = Value("creator")
  val StartDate = Value("start_date")

  implicit val format = defines.EnumUtils.enumFormat(SearchField)
}

object SearchOrder extends Enumeration {
  type Order = Value
  val Score = Value("score.desc")
  val Name = Value("name_sort.asc")
  val DateNewest = Value("lastUpdated.desc")
  val Country = Value("countryCode.asc")
  val Holder = Value("repositoryName.asc")
  val Location = Value("geodist().asc")

  implicit val format = defines.EnumUtils.enumFormat(SearchOrder)
}

object SearchType extends Enumeration {
  type Type = Value
  val All = Value("all")
  val Collection = Value("collection")
  val Authority = Value("authority")
  val Repository = Value("repository")

  implicit val format = defines.EnumUtils.enumFormat(SearchType)
}

object SearchMode extends Enumeration {
  type Type = Value
  val DefaultAll = Value("all")
  val DefaultNone = Value("none")

  implicit val format = defines.EnumUtils.enumFormat(SearchMode)
}


/**
 * Class encapsulating the parameters of a Solr search.
 *
 * User: michaelb
 */
case class SearchParams(
  query: Option[String] = None,
  page: Int = 1,
  count: Int = DEFAULT_LIST_LIMIT,
  sort: Option[SearchOrder.Value] = None,
  reverse: Option[Boolean] = Some(false),
  entities: List[EntityType.Value] = Nil,
  fields: Option[List[String]] = None,
  excludes: Option[List[String]] = None,
  filters: Option[List[String]] = None
) {

  /**
   * Is there an active constraint on these params?
   * TODO: Should this include page etc?
   */
  def isFiltered: Boolean = query.filterNot(_.trim.isEmpty).isDefined

  def offset = Math.max(0, (page - 1) * count)

  /**
   * Set unset values from another (optional) instance.
   */
  def setDefault(default: Option[SearchParams]): SearchParams = default match {
    case Some(d) => copy(
      query = query orElse d.query,
      sort = sort orElse d.sort,
      reverse = reverse orElse d.reverse,
      entities = if (entities.isEmpty) d.entities else entities,
      fields = fields orElse d.fields,
      excludes = excludes orElse d.excludes,
      filters = filters orElse d.filters
    )
    case None => this
  }
}

object SearchParams {
  val REVERSE = "desc"
  val SORT = "sort"
  val QUERY = "q"
  val FIELD = "qf"
  val ENTITY = "st"
  val EXCLUDE = "ex"
  val FILTERS = "f"

  import play.api.data.Forms._
  import play.api.data.Form

  def empty: SearchParams = new SearchParams()

  // Form deserialization
  val form = Form(
    mapping(
      QUERY -> optional(nonEmptyText),
      PAGE_PARAM -> default(number(min = 1), 1),
      COUNT_PARAM -> default(number(min = 0, max = MAX_LIST_LIMIT), DEFAULT_LIST_LIMIT),
      SORT -> optional(models.forms.enum(SearchOrder)),
      REVERSE -> optional(boolean),
      ENTITY -> list(models.forms.enum(EntityType)),
      FIELD -> optional(list(nonEmptyText)),
      EXCLUDE -> optional(list(nonEmptyText)),
      FILTERS -> optional(list(nonEmptyText))
    )(SearchParams.apply)(SearchParams.unapply)
  )

  // JSON (de)serialization
  implicit object Converter extends ClientConvertable[SearchParams] {
    implicit val clientFormat: Format[SearchParams] = Json.format[SearchParams]
  }
}