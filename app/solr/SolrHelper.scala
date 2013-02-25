package solr

import com.github.seratch.scalikesolr.request.QueryRequest
import com.github.seratch.scalikesolr.response.QueryResponse
import com.github.seratch.scalikesolr.request.query.{Query, FilterQuery, QueryParserType, Sort,StartRow,MaximumRowsReturned}
import com.github.seratch.scalikesolr.request.query.highlighting.{
    IsPhraseHighlighterEnabled, HighlightingParams}

import solr.facet.{FacetData,FacetClass,FieldFacetClass,QueryFacetClass,Facet}
import com.github.seratch.scalikesolr.request.query.facet.{FacetParams,FacetParam,Param,Value}
import concurrent.Future
import defines.EntityType


/**
 * Abstract search result page.
 * @tparam A
 */
trait Page[A] {
  val total: Long
  val page: Int
  val offset: Long
  val pageSize: Int
  val items: Seq[A]
  def numPages = (total / pageSize) + (total % pageSize).min(1)
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}

/**
 * Page of search result items
 * @param items
 * @param page
 * @param offset
 * @param pageSize
 * @param total
 * @param facets
 * @tparam A
 */
case class ItemPage[A](
  items: Seq[A],
  page: Int,
  offset: Long,
  pageSize:Int,
  total: Long,
  facets: List[FacetClass]
) extends Page[A]

/**
 * A paged list of facets.
 * @param fc
 * @param items
 * @param page
 * @param offset
 * @param pageSize
 * @param total
 * @tparam A
 */
case class FacetPage[A](
  fc: FacetClass,
  items: Seq[A],
  page: Int,
  offset: Long,
  pageSize: Int,
  total: Long
) extends Page[A]


/**
 * Helpers for dealing with Solr responses.
 */
object SolrHelper {

  private def setRequestFacets(request: QueryRequest, flist: List[FacetClass]): Unit = {
    request.setFacet(new FacetParams(
      enabled=true, 
      params=flist.map(_.asParams).flatten
    ))
  }

  /**
   *
   * @param request
   * @param facetClasses
   * @param appliedFacets
   */
  private def setRequestFilters(request: QueryRequest, facetClasses: List[FacetClass],
                                appliedFacets: Map[String,Seq[String]]): Unit = {
    // filter the results by applied facets
    // NB: Scalikesolr is a bit dim WRT filter queries: you can
    // apparently only have one. So instead of adding multiple
    // fq clauses, we need to join them all with '+'
    val fqstring = facetClasses.map(fclass => {
      appliedFacets.get(fclass.param).map(paramVals =>
        fclass match {
          case fc: FieldFacetClass => {
            paramVals.map("%s:\"%s\"".format(fc.key, _))
          }
          case fc: QueryFacetClass => {
            fc.facets.flatMap(facet => {
              if (paramVals.contains(facet.paramVal)) {
                List("%s:%s".format(fc.key, facet.solrVal))
              } else Nil
            })
          }
        }
      ).getOrElse(Nil)
    }).flatten.mkString(" +") // NB: Space before + is important
    request.setFilterQuery(FilterQuery(fqstring))
  }

  /**
   * Constrain a search request with the given facets.
   * @param request
   * @param entity
   * @param appliedFacets
   */
  def constrain(request: QueryRequest, entity: Option[EntityType.Value], appliedFacets: Map[String,Seq[String]]): Unit = {
    val flist = FacetData.getForIndex(entity)
    setRequestFacets(request, flist)
    setRequestFilters(request, flist, appliedFacets)
  }


  /**
   * Extract results from a solr response and return the facet data.
   * @param response
   * @param entity
   * @param appliedFacets
   * @return
   */
  def extract(response: QueryResponse, entity: Option[EntityType.Value],
              appliedFacets: Map[String,Seq[String]]): List[FacetClass] = {
    val rawData = xml.XML.loadString(response.rawBody)
    FacetData.getForIndex(entity).map(_.populateFromSolr(rawData, appliedFacets))
  }

  /**
   * Build a query given a set of search parameters.
   * @param params
   * @return
   */
  def buildQuery(params: SearchParams): QueryRequest = {

    // Solr 3.6 seems to break querying with *:<query> style
    // http://bit.ly/MBKghG
    //val queryString = "%s:%s".format(
    //  if (field.trim == "") "*" else field,
    //  if (query.trim == "") "*" else query)

    // FIXME: Only search by first specified field for now
    //val selector = params.fields.headOption.map(_.toString).getOrElse("*")
    // Searching specific fields not activated yet...
    // NB: For some reason, wildcards required to avoid exact-match queries...
    val queryString = "*%s*".format(params.query.getOrElse("*").trim)

    val req: QueryRequest = new QueryRequest(Query(queryString))
    req.setFacet(new FacetParams(
      enabled=true, 
      params=List(new FacetParam(Param("facet.field"), Value("type")))
    ))
    req.setQueryParserType(QueryParserType("edismax"))
    req.setHighlighting(HighlightingParams(
        enabled=true,
        isPhraseHighlighterEnabled=IsPhraseHighlighterEnabled(true)))

    val order = if(params.reversed) "desc" else "asc"
    params.sort match {
      case None => // This is the default!
      // TODO: Define these options more succinctly
      case Some(sort) => req.setSort(Sort(s"$sort $order"))
    }

    // Facet the request accordingly
    SolrHelper.constrain(req, params.entity, params.facets)

    // if we're using a specific index, constrain on that as well
    params.entity match {
      case None =>
      case Some(et) =>
          req.setFilterQuery(
            FilterQuery(req.filterQuery.fq + " +type:" + et.toString))
    }

    // Setup start and number of objects returned
    req.setStartRow(StartRow(params.offset))
    req.setMaximumRowsReturned(MaximumRowsReturned(params.limit))
    req
  }

  def buildSearchUrl(query: QueryRequest) = {
    "%s/select%s".format(
      play.Play.application.configuration.getString("solr.path"),
      query.queryString
    )
  }
}
