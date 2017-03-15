package utils.search

import defines.{BindableEnum, EntityType}
import play.api.libs.json.{Format, Json, Writes}
import play.api.mvc.QueryStringBindable
import utils.NamespaceExtractor


case object SearchField extends BindableEnum {
  type Field = Value
  val Identifier = Value("identifier")
  val Title = Value("title")
  val Creator = Value("creator")
  val Person = Value("person")
  val Place = Value("place")
  val Subject = Value("subject")
  val Address = Value("address")

  implicit val _fmt: Format[SearchField.Value] = defines.EnumUtils.enumFormat(SearchField)
}

case object SearchSort extends BindableEnum {
  type Sort = Value
  val Id = Value("isParent.desc,identifier.asc")
  val Score = Value("score.desc")
  val Name = Value("name_sort.asc")
  val DateNewest = Value("lastUpdated.desc")
  val Country = Value("countryCode.asc")
  val Holder = Value("repositoryName.asc")
  val Location = Value("geodist().asc")
  val Detail = Value("charCount.desc")
  val ChildCount = Value("childCount.desc")

  implicit val _fmt: Format[SearchSort.Value] = defines.EnumUtils.enumFormat(SearchSort)
}

case object SearchMode extends Enumeration {
  type Type = Value
  val DefaultAll = Value("all")
  val DefaultNone = Value("none")

  implicit val _fmt: Format[SearchMode.Value] = defines.EnumUtils.enumFormat(SearchMode)
}

case object FacetSort extends Enumeration {
  type FacetSort = Value
  val Name = Value("name")
  val Count = Value("count")
  val Fixed = Value("fixed")

  implicit val _fmt: Format[FacetSort.Value] = defines.EnumUtils.enumFormat(FacetSort)
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

  def toParams(ns: String = ""): Seq[(String, String)] = {
    import SearchParams._
    query.map(q => ns + QUERY -> q).toSeq ++
      sort.map(s => ns + SORT -> s.toString).toSeq ++
      entities.map(e => ns + ENTITY -> e.toString) ++
      fields.map(f => ns + FIELD -> f.toString) ++
      facets.map(f => ns + FACET -> f.toString) ++
      excludes.map(e => ns + EXCLUDE -> e) ++
      filters.map(f => ns + FILTERS -> f)
  }
}

object SearchParams {
  val SORT = "sort"
  val QUERY = "q"
  val FIELD = "qf"
  val FACET = "facet"
  val ENTITY = "st"
  val EXCLUDE = "ex"
  val FILTERS = "f"

  import defines.binders._

  def empty: SearchParams = SearchParams()

  implicit val writes: Writes[SearchParams] = Json.writes[SearchParams]

  implicit def searchParamsBinder(
    implicit intOptBinder: QueryStringBindable[Option[Int]],
    strOptBinder: QueryStringBindable[Option[String]],
    seqOptBinder: QueryStringBindable[Option[Seq[String]]],
    seqStrBinder: QueryStringBindable[Seq[String]],
    sortBinder: QueryStringBindable[Option[SearchSort.Value]]) = new QueryStringBindable[SearchParams] with NamespaceExtractor {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SearchParams]] = {
      val namespace: String = ns(key)
      Some(Right(SearchParams(
        bindOr(namespace + QUERY, params, Option.empty[String])
          .filter(_.trim.nonEmpty),
        bindOr(namespace + SORT, params, Option.empty[SearchSort.Value]),
        bindOr(namespace + ENTITY, params, Seq.empty[EntityType.Value])(
          tolerantSeqBinder(queryStringBinder(EntityType))),
        bindOr(namespace + FIELD, params, Seq.empty[SearchField.Value])(
          tolerantSeqBinder(queryStringBinder(SearchField))),
        bindOr(namespace + FACET, params, Seq.empty[String]),
        bindOr(namespace + EXCLUDE, params, Seq.empty[String]),
        bindOr(namespace + FILTERS, params, Seq.empty[String])
      )))
    }

    override def unbind(key: String, params: SearchParams): String =
      utils.http.joinQueryString(params.toParams(ns(key)).distinct)
  }
}
