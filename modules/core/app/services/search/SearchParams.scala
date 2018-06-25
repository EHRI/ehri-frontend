package services.search

import defines.EntityType
import play.api.libs.json.{Format, Json, Writes}
import utils.EnumUtils


case object SearchField extends Enumeration {
  type Field = Value
  val Identifier = Value("identifier")
  val Title = Value("title")
  val Creator = Value("creator")
  val Person = Value("person")
  val Place = Value("place")
  val Subject = Value("subject")
  val Address = Value("address")

  implicit val _fmt: Format[SearchField.Value] = EnumUtils.enumFormat(SearchField)
}

case object SearchSort extends Enumeration {
  type Sort = Value
  val Id = Value("id")
  val Score = Value("score")
  val Name = Value("name")
  val DateNewest = Value("updated")
  val Country = Value("country")
  val Holder = Value("holder")
  val Location = Value("location")
  val Detail = Value("detail")
  val ChildCount = Value("holdings")

  implicit val _fmt: Format[SearchSort.Value] = utils.EnumUtils.enumFormat(SearchSort)
}

case object SearchMode extends Enumeration {
  type Type = Value
  val DefaultAll = Value("all")
  val DefaultNone = Value("none")

  implicit val _fmt: Format[SearchMode.Value] = utils.EnumUtils.enumFormat(SearchMode)
}

case object FacetSort extends Enumeration {
  type FacetSort = Value
  val Name = Value("name")
  val Count = Value("count")
  val Fixed = Value("fixed")

  implicit val _fmt: Format[FacetSort.Value] = utils.EnumUtils.enumFormat(FacetSort)
}


/**
  * Class encapsulating the parameters of a Solr search.
  */
case class SearchParams(
  query: Option[String] = None,
  sort: Option[SearchSort.Value] = None,
  entities: Seq[EntityType.Value] = Nil,
  fields: Seq[SearchField.Value] = Nil,
  facets: Seq[String] = Nil,
  excludes: Seq[String] = Nil,
  filters: Seq[String] = Nil
) {
  /**
    * Is there an active constraint on these params?
    */
  def isFiltered: Boolean = !query.forall(_.trim.isEmpty)
}

object SearchParams {
  val SORT = "sort"
  val QUERY = "q"
  val FIELD = "qf"
  val FACET = "facet"
  val ENTITY = "st"
  val EXCLUDE = "ex"
  val FILTERS = "f"

  def empty: SearchParams = SearchParams()

  implicit val writes: Writes[SearchParams] = Json.writes[SearchParams]
}
