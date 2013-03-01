package solr

import com.github.seratch.scalikesolr.request.QueryRequest
import com.github.seratch.scalikesolr.response.QueryResponse
import com.github.seratch.scalikesolr.request.query.{Query, FilterQuery, QueryParserType, Sort,StartRow,MaximumRowsReturned,IsDebugQueryEnabled}
import com.github.seratch.scalikesolr.request.query.highlighting.{
    IsPhraseHighlighterEnabled, HighlightingParams}

import solr.facet._
import com.github.seratch.scalikesolr.request.query.facet.{FacetParams,FacetParam,Param,Value}
import defines.EntityType
import com.github.seratch.scalikesolr.response.QueryResponse
import com.github.seratch.scalikesolr.request.query.facet.Param
import solr.facet.FieldFacetClass
import scala.Some
import com.github.seratch.scalikesolr.request.query.facet.FacetParam
import com.github.seratch.scalikesolr.request.query.facet.Value
import com.github.seratch.scalikesolr.request.QueryRequest
import solr.facet.QueryFacetClass


/**
 * Page of search result items
 * @param items
 * @param offset
 * @param limit
 * @param total
 * @param facets
 * @tparam A
 */
case class ItemPage[A](
  items: Seq[A],
  offset: Int,
  limit:Int,
  total: Long,
  facets: List[FacetClass]
) extends utils.AbstractPage[A]

/**
 * A paged list of facets.
 * @param fc
 * @param items
 * @param offset
 * @param limit
 * @param total
 * @tparam A
 */
case class FacetPage[A](
  fc: FacetClass,
  items: Seq[A],
  offset: Int,
  limit: Int,
  total: Long
) extends utils.AbstractPage[A]


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
                                appliedFacets: List[AppliedFacet]): Unit = {
    // filter the results by applied facets
    // NB: Scalikesolr is a bit dim WRT filter queries: you can
    // apparently only have one. So instead of adding multiple
    // fq clauses, we need to join them all with '+'
    println("AF: " + appliedFacets)
    println("FC: " + facetClasses)
    val fqstrings = facetClasses.flatMap(fclass => {
      appliedFacets.filter(_.name == fclass.key).map(_.values).map( paramVals =>
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
      )
    }).flatten
    println("FQ: " + fqstrings)
    if (!fqstrings.isEmpty)
      request.setFilterQuery(FilterQuery("+" + fqstrings.mkString(" +")))
  }

  /**
   * Constrain a search request with the given facets.
   * @param request
   * @param entity
   * @param appliedFacets
   */
  def constrain(request: QueryRequest, entity: Option[EntityType.Value], appliedFacets: List[AppliedFacet]): Unit = {
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
              appliedFacets: List[AppliedFacet]): List[FacetClass] = {
    val rawData = xml.XML.loadString(response.rawBody)
    FacetData.getForIndex(entity).map(_.populateFromSolr(rawData, appliedFacets))
  }

  /**
   * Build a query given a set of search parameters.
   * @param params
   * @return
   */
  def buildQuery(params: SearchParams, facets: List[AppliedFacet]): QueryRequest = {

    val limit = params.limit.getOrElse(SearchParams.DEFAULT_LIMIT)

    val queryString = "%s".format(params.query.getOrElse("*").trim)

    val req: QueryRequest = new QueryRequest(Query(queryString))
    req.setFacet(new FacetParams(
      enabled=true,
      params=List(new FacetParam(Param("facet.field"), Value("type")))
    ))
    req.setQueryParserType(QueryParserType("edismax"))
    req.setHighlighting(HighlightingParams(
        enabled=true,
        isPhraseHighlighterEnabled=IsPhraseHighlighterEnabled(true)))

    val order = if(params.reversed.getOrElse(false)) "desc" else "asc"
    params.sort match {
      case None => // This is the default!
      // TODO: Define these options more succinctly
      case Some(sort) => req.setSort(Sort(s"$sort $order"))
    }

    // Apply search to specific fields. Can't find a way to do this using
    // Scalikesolr's built-in classes so we have to use it's extension-param
    // facility
    params.fields.filterNot(_.isEmpty).map { fieldList =>
      println("Fieldlist: " + fieldList)
      req.set("qf", fieldList.mkString(" "))
    }

    // Mmmn, speckcheck
    req.set("spellcheck", "true")

    // Facet the request accordingly
    SolrHelper.constrain(req, params.entity, facets)

    // if we're using a specific index, constrain on that as well
    params.entity match {
      case None =>
      case Some(et) =>
          req.setFilterQuery(
            FilterQuery(req.filterQuery.fq + " +type:" + et.toString))
    }

    // Debug query for now
    req.setIsDebugQueryEnabled(IsDebugQueryEnabled(true))

    // Setup start and number of objects returned
    params.page.map { page =>
      req.setStartRow(StartRow((Math.max(page, 1) - 1) * params.limit.getOrElse(20)))
    }
    req.setMaximumRowsReturned(MaximumRowsReturned(limit))

    req
  }

  def buildSearchUrl(query: QueryRequest) = {
    "%s/select%s".format(
      play.Play.application.configuration.getString("solr.path"),
      query.queryString
    )
  }
}
