package solr

import com.github.seratch.scalikesolr.request.query._
import com.github.seratch.scalikesolr.request.query.highlighting.{
    IsPhraseHighlighterEnabled, HighlightingParams}
import com.github.seratch.scalikesolr.request.query.facet.FacetParams
import com.github.seratch.scalikesolr.request.query.group.{GroupParams,GroupField,GroupFormat,WithNumberOfGroups}
import com.github.seratch.scalikesolr.WriterType

import defines.EntityType
import models.UserProfile
import utils.search._
import play.api.Logger
import solr.facet.FieldFacetClass
import com.github.seratch.scalikesolr.request.query.facet.Value
import com.github.seratch.scalikesolr.request.QueryRequest
import com.github.seratch.scalikesolr.request.query.facet.Param
import com.github.seratch.scalikesolr.request.query.facet.FacetParam
import com.github.seratch.scalikesolr.http.HttpMethod.POST
import solr.facet.QueryFacetClass


/**
 * Build a Solr query. This class uses the (mutable) scalikesolr
 * QueryRequest class.
 */
case class SolrQueryBuilder(writerType: WriterType, debugQuery: Boolean = false)(implicit app: play.api.Application) extends QueryBuilder {

  import SolrConstants._

  /**
   * Look up boost values from configuration for default query fields.
   */
  private lazy val queryFieldsWithBoost: Seq[(String,Option[Double])] = Seq(
    ITEM_ID, NAME_EXACT, NAME_MATCH, OTHER_NAMES, PARALLEL_NAMES, NAME_SORT, TEXT
  ).map(f => f -> app.configuration.getDouble(s"ehri.search.boost.$f"))

  private lazy val spellcheckParams: Seq[(String,Option[String])] = Seq(
    "count", "onlyMorePopular", "extendedResults", "accuracy",
    "collate", "maxCollations", "maxCollationTries"
  ).map(f => f -> app.configuration.getString(s"ehri.search.spellcheck.$f"))


  /**
   * Set a list of facets on a request.
   */
  private def setRequestFacets(request: QueryRequest, flist: FacetClassList): Unit = {

    // Need to tag and exclude all 'choice' facet classes because we want
    // the counts even if they're excluded...
    request.setFacet(new FacetParams(
      enabled=true,
      params=flist.flatMap {
        case qf: QueryFacetClass => List(qf.asParams)
        case ff: FieldFacetClass => List(ff.asParams)
        case e => {
          Logger.logger.warn("Unknown facet class type: {}", e)
          Nil
        }
      }.flatten
    ))
  }

  /**
   * Apply filters to the request based on a set of applied facets.
   */
  private def setRequestFilters(request: QueryRequest, facetClasses: FacetClassList,
                                appliedFacets: List[AppliedFacet]): Unit = {
    // filter the results by applied facets
    // NB: Scalikesolr is a bit dim WRT filter queries: you can
    // apparently only have one. So instead of adding multiple
    // fq clauses, we need to join them all with '+'

    val fqstrings = facetClasses.flatMap(fclass => {
      appliedFacets.filter(_.name == fclass.key).map(_.values).map { paramVals =>
        // If we get several field parameters for the same facet class we
        // need to OR them together...
        fclass match {
          case fc: FieldFacetClass => {
            paramVals match {
              case Nil => Nil
              case _ => {
                // Choice facets need a tag in front of the parameter so they can be
                // excluded from count-limiting filters
                // http://wiki.apache.org/solr/SimpleFacetParameters#Multi-Select_Faceting_and_LocalParams
                val query = "(" + paramVals.map(v => fc.key + ":\"" + v + "\"").mkString(" OR ") + ")"
                if (fc.multiSelect) List("{!tag=" + fc.key + "}" + query)
                else List(query)
              }
            }
          } // Grr, interpolation...
          case fc: QueryFacetClass => {
            fc.facets.flatMap(facet => {
              if (paramVals.contains(facet.value)) {
                List(s"{!tag=${fc.key}}${fc.key}:${facet.solrValue}")
              } else Nil
            })
          }
          case e => {
            Logger.logger.warn("Unknown facet class type: {}", e)
            Nil
          }
        }
      }
    }).flatten
    request.setFilterQuery(FilterQuery(multiple = fqstrings))
    request.set("facet.mincount", 1)
  }

  /**
   * Constrain a search request with the given facets.
   */
  private def constrain(request: QueryRequest, appliedFacets: List[AppliedFacet], allFacets: FacetClassList): Unit = {
    setRequestFacets(request, allFacets)
    setRequestFilters(request, allFacets, appliedFacets)
  }

  /**
   * Constrain the search to entities of a given type, applying an fq
   * parameter to the "type" field.
   */
  private def constrainEntities(request: QueryRequest, entities: List[EntityType.Value]): Unit = {
    if (!entities.isEmpty) {
      val filter = entities.map(_.toString).mkString(" OR ")
      request.setFilterQuery(
        FilterQuery(multiple = request.filterQuery.getMultiple ++ Seq(s"$TYPE:($filter)")))
    }
  }

  /**
   * Filter docs based on access. If the user is empty, only allow
   * through those which have accessibleTo:ALLUSERS.
   * If we have a user and they're not admin, add a filter against
   * all their groups.
   */
  private def applyAccessFilter(request: QueryRequest, userOpt: Option[UserProfile]): Unit = {
    if (userOpt.isEmpty) {
      request.setFilterQuery(
        FilterQuery(multiple = request.filterQuery.getMultiple ++
          Seq("%s:%s".format(ACCESSOR_FIELD, ACCESSOR_ALL_PLACEHOLDER))))
    } else if (!userOpt.get.isAdmin) {
      // Create a boolean or query starting with the ALL placeholder, which
      // includes all the groups the user belongs to, included inherited ones,
      // i.e. accessibleTo:(ALLUSERS OR mike OR admin)
      val accessors = ACCESSOR_ALL_PLACEHOLDER :: userOpt.map(
          u => (u.id :: u.allGroups.map(_.id)).distinct).getOrElse(Nil)
      request.setFilterQuery(
        FilterQuery(multiple = request.filterQuery.getMultiple ++ Seq("%s:(%s)".format(
          ACCESSOR_FIELD, accessors.mkString(" OR ")))))
    }
  }

  /**
   * Group results by item id (as opposed to description id). Facet counts
   * are also set to reflect grouping as opposed to the number of individual
   * items.
   */
  private def setGrouping(request: QueryRequest): Unit = {
    request.setGroup(GroupParams(
      enabled=true,
      field=GroupField(ITEM_ID),
      format=GroupFormat("simple"),
      ngroups=WithNumberOfGroups(ngroups = true)
    ))

    // Not yet supported by scalikesolr
    request.set("group.facet", true)
  }

  /**
   * Run a simple filter on the name_ngram field of all entities
   * of a given type.
   */
  def simpleFilter(params: SearchParams, filters: Map[String,Any] = Map.empty, extra: Map[String,Any] = Map.empty, alphabetical: Boolean = false)(
      implicit userOpt: Option[UserProfile]): QueryRequest = {

    val excludeIds = params.excludes.toList.flatten.map(id => s" -$ITEM_ID:$id").mkString
    val queryString = params.query.getOrElse("*").trim + excludeIds

    val req: QueryRequest = new QueryRequest(Query(queryString))
    constrainEntities(req, params.entities)
    applyAccessFilter(req, userOpt)
    setGrouping(req)
    req.set("qf", s"$NAME_MATCH^2.0 $NAME_NGRAM")
    req.setFieldsToReturn(FieldsToReturn(s"$ID $ITEM_ID $NAME_EXACT $TYPE $HOLDER_NAME $DB_ID"))
    if (alphabetical) req.setSort(Sort(s"$NAME_SORT asc"))
    req.setQueryParserType(QueryParserType("edismax"))

    // Setup start and number of objects returned
    req.setStartRow(StartRow(params.offset))

    req.setMaximumRowsReturned(MaximumRowsReturned(params.count))
    req.setWriterType(writerType)

    extra.map { case (key, value) =>
      req.set(key, value)
    }



    req
  }


  /**
   * Build a query given a set of search parameters.
   */
  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList,
             filters: Map[String,Any] = Map.empty,
             extra: Map[String,Any] = Map.empty,
             mode: SearchMode.Value = SearchMode.DefaultAll)(
      implicit userOpt: Option[UserProfile]): QueryRequest = {

    val excludeIds = params.excludes.toList.flatten.map(id => s" -$ITEM_ID:$id").mkString

    val searchFilters = params.filters.toList.flatten.filter(_.contains(":")).map(f => " +" + f).mkString

    val defaultQuery = mode match {
      case SearchMode.DefaultAll => "*"
      case _ => "PLACEHOLDER_QUERY_RETURNS_NO_RESULTS" // FIXME! This sucks
    }

    // Child count to boost results seems to have an odd affect in making the
    // query only work on the default field - disabled for now...
    val queryString =
        //s"{!boost b=$CHILD_COUNT}" +
        params.query.getOrElse(defaultQuery).trim + excludeIds + searchFilters
      val spellcheckQueryString = params.query.getOrElse(defaultQuery).trim

    val req: QueryRequest = new QueryRequest(Query(queryString))

    // Always facet on item type
    req.setFacet(new FacetParams(
      enabled=true,
      params=List(new FacetParam(Param("facet.field"), Value(TYPE)))
    ))

    // Use edismax to parse the user query
    req.setQueryParserType(QueryParserType("edismax"))

    // Highlight, but only if we have a query...
    if (params.query.isDefined) {
      //req.set("highlight.q", params.query)
      req.setHighlighting(HighlightingParams(
          enabled=true,
          isPhraseHighlighterEnabled=IsPhraseHighlighterEnabled(usePhraseHighlighter = true)))
    }

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
      val qfFields: String = queryFieldsWithBoost.map { case (key, boostOpt) =>
        boostOpt.map(b => s"$key^$b").getOrElse(key)
      }.mkString(" ")
      Logger.debug(s"Query fields: $qfFields")
      req.set("qf", qfFields)
    }

    // Mmmn, speckcheck
    req.set("spellcheck", "true")
    req.set("spellcheck.q", spellcheckQueryString)

    spellcheckParams.collect { case (key, Some(value)) =>
      req.set(s"spellcheck.$key", value)
    }

    // Facet the request accordingly
    constrain(req, facets, allFacets)

    // if we're using a specific index, constrain on that as well
    constrainEntities(req, params.entities)

    // Currently returning all the fields, but this might change...
    //req.setFieldsToReturn(FieldsToReturn(s"$ID $ITEM_ID $TYPE $DB_ID"))

    // Return only fields we care about...
    applyAccessFilter(req, userOpt)

    // Apply other arbitrary hard filters
    filters.map { case (key, value) =>
      val filter = value match {
        // Have to quote strings
        case s: String => "%s:\"%s\"".format(key, value)
        // not value means the key is a query!
        case Unit => key
        case _ => s"$key:$value"
      }
      req.setFilterQuery(FilterQuery(multiple = req.filterQuery.getMultiple ++ Seq(filter)))
    }


    // Debug query for now
    req.setIsDebugQueryEnabled(IsDebugQueryEnabled(debugQuery = debugQuery))

    // Setup start and number of objects returned
    req.setStartRow(StartRow(params.offset))
    req.setMaximumRowsReturned(MaximumRowsReturned(params.count))

    // Group results by item id, as opposed to the document-level
    // description (for non-multi-description entities this will
    // be the same)
    setGrouping(req)

    // Set JSON writer type!
    req.setWriterType(writerType)

    extra.map { case (key, value) =>
      req.set(key, value)
    }

    println(req)
    println(params)
    println(filters)
    println(extra)

    req
  }
}
