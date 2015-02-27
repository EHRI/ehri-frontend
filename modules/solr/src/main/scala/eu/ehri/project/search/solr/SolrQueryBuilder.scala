package eu.ehri.project.search.solr

import com.github.seratch.scalikesolr.request.query._
import com.github.seratch.scalikesolr.request.query.highlighting.{
    IsPhraseHighlighterEnabled, HighlightingParams}
import com.github.seratch.scalikesolr.request.query.facet.FacetParams
import com.github.seratch.scalikesolr.request.query.group.{GroupParams,GroupField,GroupFormat,WithNumberOfGroups}

import defines.EntityType
import models.UserProfile
import utils.search._
import play.api.Logger
import com.github.seratch.scalikesolr.request.query.facet.Value
import com.github.seratch.scalikesolr.request.QueryRequest
import com.github.seratch.scalikesolr.request.query.facet.Param
import com.github.seratch.scalikesolr.request.query.facet.FacetParam
import com.github.seratch.scalikesolr.{WriterType => SWriterType}


object SolrQueryBuilder {

  /**
   * Set a list of facets on a request.
   */
  def getRequestFacets(flist: Seq[FacetClass[Facet]]): Seq[FacetParam] =
    flist.map(SolrFacetParser.facetAsParams).flatten

  /**
   * Apply filters to the request based on a set of applied facets.
   */
  def getRequestFilters(facetClasses: Seq[FacetClass[Facet]],
                                appliedFacets: Seq[AppliedFacet]): Seq[String] = {
    // See the spec for this to get some insight
    // into how this mess works...

    // filter the results by applied facets
    facetClasses.flatMap { fclass =>
      appliedFacets.filter(_.name == fclass.key).map(_.values).flatMap { paramVals =>
        if (paramVals.isEmpty) None
        else {
          val query: Option[String] = fclass match {
            case fc: FieldFacetClass =>
              // Choice facets need a tag in front of the parameter so they can be
              // excluded from count-limiting filters
              // http://wiki.apache.org/solr/SimpleFacetParameters#Multi-Select_Faceting_and_LocalParams
              val filter = paramVals.map(v => "\"" + v + "\"").mkString(" ")
              Some(s"${fc.key}:($filter)")
            case fc: QueryFacetClass =>
              val activeRanges = fc.facets.filter(f => paramVals.contains(f.value))
              val filter = activeRanges.map(SolrFacetParser.facetValue).mkString(" ")
              Some(s"${fc.key}:($filter)")
            case e =>
              Logger.logger.warn("Unknown facet class type: {}", e)
              None
          }
          query.map { q =>
            val tag = if (fclass.multiSelect) "{!tag=" + fclass.key + "}" else ""
            tag + q
          }
        }
      }
    }
  }
}


/**
 * Build a Solr query. This class uses the (mutable) scalikesolr
 * QueryRequest class.
 */
case class SolrQueryBuilder(
  writerType: WriterType.Value,
  debugQuery: Boolean = false,
  params: SearchParams = SearchParams.empty,
  facets: Seq[AppliedFacet] = Seq.empty,
  facetClasses: Seq[FacetClass[Facet]] = Seq.empty,
  filters: Map[String,Any] = Map.empty,
  idFilters: Seq[String] = Seq.empty,
  extraParams: Map[String,Any] = Map.empty,
  mode: SearchMode.Value = SearchMode.DefaultAll
)(implicit app: play.api.Application) extends QueryBuilder {

  import SearchConstants._
  import SolrQueryBuilder._

  /**
   * Look up boost values from configuration for default query fields.
   */
  private lazy val queryFieldsWithBoost: Seq[(String,Option[Double])] = Seq(
    ITEM_ID, IDENTIFIER, NAME_EXACT, NAME_MATCH, OTHER_NAMES, PARALLEL_NAMES, ALT_NAMES, NAME_SORT, TEXT
  ).map(f => f -> app.configuration.getDouble(s"ehri.search.boost.$f"))

  private lazy val spellcheckParams: Seq[(String,Option[String])] = Seq(
    "count", "onlyMorePopular", "extendedResults", "accuracy",
    "collate", "maxCollations", "maxCollationTries", "maxResultsForSuggest"
  ).map(f => f -> app.configuration.getString(s"ehri.search.spellcheck.$f"))


  /**
   * Constrain the search to entities of a given type, applying an fq
   * parameter to the "type" field.
   */
  private def constrainEntities(request: QueryRequest, entities: List[EntityType.Value]): Unit = {
    if (entities.nonEmpty) {
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
          Seq(s"$ACCESSOR_FIELD:$ACCESSOR_ALL_PLACEHOLDER")))
    } else if (!userOpt.exists(_.isAdmin)) {
      // Create a boolean or query starting with the ALL placeholder, which
      // includes all the groups the user belongs to, included inherited ones,
      // i.e. accessibleTo:(ALLUSERS OR mike OR admin)
      val accessors = ACCESSOR_ALL_PLACEHOLDER :: userOpt.map(
          u => (u.id :: u.allGroups.map(_.id)).distinct).getOrElse(Nil)

      request.setFilterQuery(
        FilterQuery(multiple = request.filterQuery.getMultiple ++
          Seq(s"$ACCESSOR_FIELD:(${accessors.mkString(" ")})")))
    }
  }



  /**
   * Constrain a search request with the given facets.
   */
  private def constrainToFacets(request: QueryRequest, appliedFacets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]]): Unit = {
    request.setFacet(new FacetParams(
      enabled=true,
      params=getRequestFacets(allFacets).toList
    ))

    request.setFilterQuery(FilterQuery(multiple = getRequestFilters(allFacets, appliedFacets)))
    request.set("facet.mincount", 1)
  }

  /**
   * Group results by item id (as opposed to description id). Facet counts
   * are also set to reflect grouping as opposed to the number of individual
   * items.
   *
   * NOTE: Scalikesolr insists we must set the start and rows parameter
   * here otherwise it will add default values to the query!
   */
  private def setGrouping(request: QueryRequest, params: SearchParams): Unit = {
    request.setGroup(GroupParams(
      enabled=true,
      field=GroupField(ITEM_ID),
      format=GroupFormat("simple"),
      ngroups=WithNumberOfGroups(ngroups = true),
      start = StartRow(params.offset),
      rows = MaximumRowsReturned(params.countOrDefault)
    ))

    // Not yet supported by scalikesolr
    request.set("group.facet", true)
  }

  private def applyIdFilters(request: QueryRequest, ids: Seq[String]): Unit = {
    if (ids.nonEmpty) {
      request
        .setFilterQuery(FilterQuery(s"$ITEM_ID:(${ids.mkString(" ")})"))
    }
  }

  /**
   * Run a simple filter on the name_ngram field of all entities
   * of a given type.
   */
  override def simpleFilterQuery(alphabetical: Boolean = false)(implicit userOpt: Option[UserProfile]): Map[String,Seq[String]] = {

    val excludeIds = params.excludes.toList.flatten.map(id => s" -$ITEM_ID:$id").mkString
    val queryString = params.query.getOrElse("*").trim + excludeIds

    val req: QueryRequest = QueryRequest(
      query = Query(queryString),
      writerType = SWriterType.as(writerType.toString),
      startRow = StartRow(params.offset),
      maximumRowsReturned = MaximumRowsReturned(params.countOrDefault),
      isDebugQueryEnabled = IsDebugQueryEnabled(debugQuery = debugQuery),
      queryParserType = QueryParserType("edismax")
    )

    constrainEntities(req, params.entities)
    applyAccessFilter(req, userOpt)
    applyIdFilters(req, idFilters)
    setGrouping(req, params)
    req.set("qf", s"$NAME_MATCH^2.0 $NAME_NGRAM")
    req.setFieldsToReturn(FieldsToReturn(s"$ID $ITEM_ID $NAME_EXACT $TYPE $HOLDER_NAME $DB_ID"))
    if (alphabetical) req.setSort(Sort(s"$NAME_SORT asc"))

    extraParams.map { case (key, value) =>
      req.set(key, value)
    }

    utils.parseQueryString(req.queryString())
  }


  /**
   * Build a query given a set of search parameters.
   */
  override def searchQuery()(implicit userOpt: Option[UserProfile]): Map[String,Seq[String]] = {

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

    val req: QueryRequest = QueryRequest(
      query = Query(queryString),
      writerType = SWriterType.as(writerType.toString),
      startRow = StartRow(params.offset),
      maximumRowsReturned = MaximumRowsReturned(params.countOrDefault),
      isDebugQueryEnabled = IsDebugQueryEnabled(debugQuery = debugQuery),
      queryParserType = QueryParserType("edismax")
    )

    //println(s"REQUEST: $req, STRING: ${req.queryString()}")

    // Always facet on item type
    req.setFacet(new FacetParams(
      enabled=true,
      params=List(new FacetParam(Param("facet.field"), Value(TYPE)))
    ))

    // Highlight, but only if we have a query...
    if (params.query.isDefined) {
      //req.set("highlight.q", params.query)
      req.setHighlighting(HighlightingParams(
          enabled=true,
          isPhraseHighlighterEnabled=IsPhraseHighlighterEnabled(usePhraseHighlighter = true)))
      req.set("hl.simple.pre", "<em class='highlight'>")
      req.set("hl.simple.post", "</em>")
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
      Logger.trace(s"Query fields: $qfFields")
      req.set("qf", qfFields)
    }

    // Mmmn, speckcheck
    req.set("spellcheck", "true")
    spellcheckParams.collect { case (key, Some(value)) =>
      req.set(s"spellcheck.$key", value)
    }

    // Facet the request accordingly
    constrainToFacets(req, facets, facetClasses)

    // if we're using a specific index, constrain on that as well
    constrainEntities(req, params.entities)

    // Currently returning all the fields, but this might change...
    //req.setFieldsToReturn(FieldsToReturn(s"$ID $ITEM_ID $TYPE $DB_ID"))

    // Return only fields we care about...
    applyAccessFilter(req, userOpt)

    // Constrain to specific ids
    applyIdFilters(req, idFilters)

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

    // Group results by item id, as opposed to the document-level
    // description (for non-multi-description entities this will
    // be the same)
    // NB: Group params ALSO set (or override) start and row parameters, which
    // is a major gotcha!
    setGrouping(req, params)

    extraParams.map { case (key, value) =>
      req.set(key, value)
    }

    // FIXME: It's RUBBISH to parse the output of scalikesolr's query string
    // TODO: Implement a light-weight request builder
    utils.parseQueryString(req.queryString())
  }

  override def withIdFilters(ids: Seq[String]): QueryBuilder = copy(idFilters = idFilters ++ ids)

  override def withFacets(f: Seq[AppliedFacet]): QueryBuilder = copy(facets = facets ++ f)

  override def setMode(mode: SearchMode.Value): QueryBuilder = copy(mode = mode)

  override def withFilters(f: Map[String, Any]): QueryBuilder = copy(filters = filters ++ f)

  override def setParams(params: SearchParams): QueryBuilder = copy(params = params)

  override def withFacetClasses(fc: Seq[FacetClass[Facet]]): QueryBuilder = copy(facetClasses = facetClasses ++ fc)

  override def withExtraParams(extra: Map[String, Any]): QueryBuilder = copy(extraParams = extraParams ++ extra)
}
