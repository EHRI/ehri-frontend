package solr

import com.github.seratch.scalikesolr.request.QueryRequest
import com.github.seratch.scalikesolr.response.QueryResponse
import com.github.seratch.scalikesolr.request.query.{Query, FilterQuery, QueryParserType, Sort,StartRow,MaximumRowsReturned,IsDebugQueryEnabled}
import com.github.seratch.scalikesolr.request.query.FieldsToReturn
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
import models.UserProfile


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

  import SolrIndexer._

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
    request.setFilterQuery(FilterQuery(multiple = fqstrings))
  }

  /**
   * Constrain a search request with the given facets.
   * @param request
   * @param appliedFacets
   * @param allFacets
   */
  def constrain(request: QueryRequest, appliedFacets: List[AppliedFacet], allFacets: List[FacetClass]): Unit = {
    setRequestFacets(request, allFacets)
    setRequestFilters(request, allFacets, appliedFacets)
  }


  /**
   * Extract results from a solr response and return the facet data.
   * @param response
   * @param appliedFacets
   * @param allFacets
   * @return
   */
  def extract(response: String, appliedFacets: List[AppliedFacet], allFacets: List[FacetClass]): List[FacetClass] = {
    val rawData = xml.XML.loadString(response)
    allFacets.map(_.populateFromSolr(rawData, appliedFacets))
  }

  /**
   * Build a query given a set of search parameters.
   * @param params
   * @return
   */
  def buildQuery(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): QueryRequest = {

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

    val order = if(params.reverse.getOrElse(false)) "desc" else "asc"
    params.sort match {
      case None => // This is the default!
      // TODO: Define these options more succinctly
      case Some(sort) => req.setSort(Sort(s"$sort $order"))
    }

    // Apply search to specific fields. Can't find a way to do this using
    // Scalikesolr's built-in classes so we have to use it's extension-param
    // facility
    params.fields.filterNot(_.isEmpty).map { fieldList =>
      req.set("qf", fieldList.mkString(" "))
    }

    // Mmmn, speckcheck
    //req.set("spellcheck", "true")

    // Facet the request accordingly
    SolrHelper.constrain(req, facets, allFacets)

    // if we're using a specific index, constrain on that as well
    if (!params.entities.isEmpty) {
      val filter = params.entities.map(_.toString).mkString(" OR ")
      req.setFilterQuery(
          FilterQuery(multiple = req.filterQuery.getMultiple() ++ Seq(s"type:($filter)")))
    }

    // Testing...
    req.setFieldsToReturn(FieldsToReturn("id itemId type"))

    // Filter docs based on access. If the user is empty, only allow
    // through those which have accessibleTo:ALLUSERS.
    // If we have a user and they're not admin, add a filter against
    // all their groups
    if (userOpt.isEmpty) {
      req.setFilterQuery(
        FilterQuery(multiple = req.filterQuery.getMultiple() ++ Seq("%s:%s".format(ACCESSOR_FIELD, ACCESSOR_ALL_PLACEHOLDER))))
    } else if (!userOpt.get.isAdmin) {
      // Create a boolean or query starting with the ALL placeholder, which
      // includes all the groups the user belongs to, included inherited ones,
      // i.e. accessibleTo:(ALLUSERS OR mike OR admin)
      val accessors = SolrIndexer.ACCESSOR_ALL_PLACEHOLDER :: userOpt.map(u => (u.id :: u.allGroups.map(_.id)).distinct).getOrElse(Nil)
      req.setFilterQuery(
        FilterQuery(multiple = req.filterQuery.getMultiple() ++ Seq("%s:(%s)".format(ACCESSOR_FIELD, accessors.mkString(" OR ")))))
    }

    // Apply other arbitrary hard filters
    filters.map { case (key, value) =>
      val filter = value match {
        case s: String => "%s:\"%s\"".format(key, s)
        case _: Int => "%s:%s".format(key, value)
      }
      req.setFilterQuery(FilterQuery(multiple = req.filterQuery.getMultiple() ++ Seq(filter)))
    }

    // Debug query for now
    req.setIsDebugQueryEnabled(IsDebugQueryEnabled(true))

    // Setup start and number of objects returned
    params.page.map { page =>
      req.setStartRow(StartRow((Math.max(page, 1) - 1) * params.limit.getOrElse(20)))
    }
    req.setMaximumRowsReturned(MaximumRowsReturned(limit))

    // Group by features to group results by the item id
    req.set("group", true)
    req.set("group.field", "itemId")
    req.set("group.facet", true)
    req.set("group.format", "simple")
    req.set("group.ngroups", true)
    //req.set("group.truncate", true)

    req
  }

  def buildSearchUrl(query: QueryRequest) = {
    "%s/select%s".format(
      play.Play.application.configuration.getString("solr.path"),
      query.queryString
    )
  }
}
