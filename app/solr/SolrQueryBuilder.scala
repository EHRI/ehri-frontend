package solr

import com.github.seratch.scalikesolr.request.QueryRequest
import com.github.seratch.scalikesolr.request.query.{Query, FilterQuery, QueryParserType,
    Sort,StartRow,MaximumRowsReturned,IsDebugQueryEnabled}
import com.github.seratch.scalikesolr.request.query.FieldsToReturn
import com.github.seratch.scalikesolr.request.query.highlighting.{
    IsPhraseHighlighterEnabled, HighlightingParams}
import com.github.seratch.scalikesolr.request.query.facet.{FacetParams,FacetParam,Param,Value}

import solr.facet._

import models.UserProfile
import defines.EntityType



/**
 * Helpers for dealing with Solr responses.
 */
object SolrQueryBuilder {

  import SolrIndexer._

  private def setRequestFacets(request: QueryRequest, flist: List[FacetClass]): Unit = {
    request.setFacet(new FacetParams(
      enabled=true,
      params=flist.map(_.asParams).flatten
    ))
  }

  /**
   * Apply filters to the request based on a set of applied facets.
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
              if (paramVals.contains(facet.param)) {
                List("%s:%s".format(fc.key, facet.solr))
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
  private def constrain(request: QueryRequest, appliedFacets: List[AppliedFacet], allFacets: List[FacetClass]): Unit = {
    setRequestFacets(request, allFacets)
    setRequestFilters(request, allFacets, appliedFacets)
  }

  private def constrainEntities(request: QueryRequest, entities: List[EntityType.Value]): Unit = {
    if (!entities.isEmpty) {
      val filter = entities.map(_.toString).mkString(" OR ")
      request.setFilterQuery(
        FilterQuery(multiple = request.filterQuery.getMultiple() ++ Seq(s"type:($filter)")))
    }
  }

  /**
   * Filter docs based on access. If the user is empty, only allow
   * through those which have accessibleTo:ALLUSERS.
   * If we have a user and they're not admin, add a filter against
   * all their groups
   * @param request
   * @param userOpt
   */
  private def applyAccessFilter(request: QueryRequest, userOpt: Option[UserProfile]): Unit = {
    if (userOpt.isEmpty) {
      request.setFilterQuery(
        FilterQuery(multiple = request.filterQuery.getMultiple() ++
          Seq("%s:%s".format(ACCESSOR_FIELD, ACCESSOR_ALL_PLACEHOLDER))))
    } else if (!userOpt.get.isAdmin) {
      // Create a boolean or query starting with the ALL placeholder, which
      // includes all the groups the user belongs to, included inherited ones,
      // i.e. accessibleTo:(ALLUSERS OR mike OR admin)
      val accessors = ACCESSOR_ALL_PLACEHOLDER :: userOpt.map(
          u => (u.id :: u.allGroups.map(_.id)).distinct).getOrElse(Nil)
      request.setFilterQuery(
        FilterQuery(multiple = request.filterQuery.getMultiple() ++ Seq("%s:(%s)".format(
          ACCESSOR_FIELD, accessors.mkString(" OR ")))))
    }
  }

  /**
   * Group results by item id (as opposed to description id)
   * @param request
   */
  private def setGrouping(request: QueryRequest): Unit = {
    request.set("group", true)
    request.set("group.field", "itemId")
    request.set("group.facet", true)
    request.set("group.format", "simple")
    request.set("group.ngroups", true)
    //req.set("group.truncate", true)
  }

  /**
   * Run a simple filter on the name_ngram field of all entities
   * of a given type.
   * @param q
   * @param entityType
   * @param page
   * @param limitOpt
   * @param userOpt
   * @return
   */
  def simpleFilter(q: String, entityType: Option[EntityType.Value], page: Option[Int] = Some(1), limitOpt: Option[Int] = Some(100),
                    alphabetical: Boolean = false)(
    implicit userOpt: Option[UserProfile]): QueryRequest = {

    val queryString = if(q.trim.isEmpty) "*" else q

    val req: QueryRequest = new QueryRequest(Query(queryString))
    constrainEntities(req, entityType.toList)
    applyAccessFilter(req, userOpt)
    setGrouping(req)
    req.set("qf", "title^2.0 name_ngram")
    req.setFieldsToReturn(FieldsToReturn("id itemId name type"))
    if (alphabetical) req.setSort(Sort("name_sort asc"))
    req.setQueryParserType(QueryParserType("edismax"))
    // Setup start and number of objects returned
    val limit = limitOpt.getOrElse(SearchParams.DEFAULT_LIMIT)
    page.map { page =>
      req.setStartRow(StartRow((Math.max(page, 1) - 1) * limit))
    }
    req.setMaximumRowsReturned(MaximumRowsReturned(limit))

    req
  }


  /**
   * Build a query given a set of search parameters.
   * @param params
   * @return
   */
  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): QueryRequest = {

    val queryString = params.query.getOrElse("*").trim

    val req: QueryRequest = new QueryRequest(Query(queryString))

    // Always facet on item type
    req.setFacet(new FacetParams(
      enabled=true,
      params=List(new FacetParam(Param("facet.field"), Value("type")))
    ))

    // Use edismax to parse the user query
    req.setQueryParserType(QueryParserType("edismax"))

    // Highlight, which will at some point be implemented...
    req.setHighlighting(HighlightingParams(
        enabled=true,
        isPhraseHighlighterEnabled=IsPhraseHighlighterEnabled(true)))

    // Set result ordering, defaulting to the solr default 'score asc'
    // (but we have to specify this to allow 'score desc' ??? (Why is this needed?)
    // FIXME: This horrid concatenation of name/order
    params.sort.map { sort =>
      req.setSort(Sort(s"${sort.toString.split("""\.""").mkString(" ")}"))
    }

    // Apply search to specific fields. Can't find a way to do this using
    // Scalikesolr's built-in classes so we have to use it's extension-param
    // facility
    params.fields.filterNot(_.isEmpty).map { fieldList =>
      req.set("qf", fieldList.mkString(" "))
    } getOrElse {
      req.set("qf", "title^3.0 text")
    }

    // Mmmn, speckcheck
    // TODO: Add quality params here...
    req.set("spellcheck", "true")
    req.set("spellcheck.q", queryString)
    req.set("spellcheck.extendedResults", "true")

    // Facet the request accordingly
    constrain(req, facets, allFacets)

    // if we're using a specific index, constrain on that as well
    constrainEntities(req, params.entities)

    // Only return what we immediately need to build a SearchDescription. We
    // ignore nearly everything currently stored in Solr, instead fetching the
    // data from the DB, but this might change in future.
    req.setFieldsToReturn(FieldsToReturn("id itemId type"))

    // Return only fields we care about...
    applyAccessFilter(req, userOpt)

    // Apply other arbitrary hard filters
    filters.map { case (key, value) =>
      val filter = value match {
        case s: String => "%s:\"%s\"".format(key, s)
        case _: Int => "%s:%s".format(key, value)
      }
      req.setFilterQuery(FilterQuery(multiple = req.filterQuery.getMultiple() ++ Seq(filter)))
    }

    // Debug query for now
    //req.setIsDebugQueryEnabled(IsDebugQueryEnabled(true))

    // Setup start and number of objects returned
    val limit = params.limit.getOrElse(SearchParams.DEFAULT_LIMIT)
    params.page.map { page =>
      req.setStartRow(StartRow((Math.max(page, 1) - 1) * params.limit.getOrElse(20)))
    }
    req.setMaximumRowsReturned(MaximumRowsReturned(limit))

    // Group results by item id, as opposed to the document-level
    // description (for non-multi-description entities this will
    // be the same)
    setGrouping(req)

    req
  }
}
