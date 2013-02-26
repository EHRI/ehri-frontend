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
  query: Option[String] = None,
  page: Option[Int] = None,
  limit: Option[Int] = None,
  sort: Option[SearchOrder.Value] = None,
  reversed: Option[Boolean] = Some(false),
  entity: Option[EntityType.Value] = None,
  fields: Option[List[String]] = None,
  facets: Option[List[facet.AppliedFacet]] = None
)

object SearchParams {
  import play.api.data.Forms._
  import play.api.data.Form

  val form = Form(
    mapping(
      "q" -> optional(nonEmptyText),
      "page" -> optional(number),
      "limit" -> optional(number),
      "sort" -> optional(models.forms.enum(SearchOrder)),
      "order" -> optional(boolean),
      "st" -> optional(models.forms.enum(EntityType)),
      "qf" -> optional(list(nonEmptyText)),
      "f" -> optional(list(
        mapping("n" -> nonEmptyText, "v" -> list(nonEmptyText))(facet.AppliedFacet.apply _)(facet.AppliedFacet.unapply _)
      ))
    )(SearchParams.apply _)(SearchParams.unapply _)
  )
}
