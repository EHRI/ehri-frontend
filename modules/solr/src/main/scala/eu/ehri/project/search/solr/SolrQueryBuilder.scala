package eu.ehri.project.search.solr

import defines.EntityType
import models.UserProfile
import utils.search._
import play.api.{Configuration, Logger}
import utils.PageParams


object SolrQueryBuilder {

  def escape(s: CharSequence): String = {
    val sb: StringBuffer = new StringBuffer()
    0.until(s.length()).foreach { i =>
      val c = s.charAt(i)
      if (c == '\\' || c == '!' || c == '(' || c == ')' ||
        c == ':'  || c == '^' || c == '[' || c == ']' ||
        c == '{'  || c == '}' || c == '~' || c == '*' || c == '?' ||
        c == '"'  || c == ' '
      ) {
        sb.append('\\')
      }
      sb.append(c);
    }
    sb.toString
  }

  /**
   * Apply filters to the request based on a set of applied facets.
   */
  def facetFilters(facetClasses: Seq[FacetClass[Facet]],
                                appliedFacets: Seq[AppliedFacet]): Seq[(String, String)] = {
    // See the spec for this to get some insight
    // into how this mess works...

    // filter the results by applied facets
    val filters = facetClasses.flatMap { fclass =>
      appliedFacets.filter(_.name == fclass.key).map(_.values).flatMap { paramVals =>
        if (paramVals.isEmpty) None
        else {
          val query: Option[String] = fclass match {
            case fc: FieldFacetClass =>
              // Choice facets need a tag in front of the parameter so they can be
              // excluded from count-limiting filters
              // http://wiki.apache.org/solr/SimpleFacetParameters#Multi-Select_Faceting_and_LocalParams
              val filter = paramVals.map(s => "\"" + escape(s) + "\"").mkString(" ")
              Some(s"${fc.key}:($filter)")
            case fc: QueryFacetClass =>
              val activeRanges = fc.facets.filter(f => paramVals.contains(f.value))
              if (activeRanges.nonEmpty) {
                val filter = activeRanges.map(SolrFacetParser.facetValue).mkString(" ")
                Some(s"${fc.key}:($filter)")
              } else None
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
    filters.map(f => "fq" -> f)
  }
}


/**
 * Build a Solr query. This class uses the (mutable) scalikesolr
 * QueryRequest class.
 */
case class SolrQueryBuilder(
  query: SearchQuery,
  debugQuery: Boolean = false
)(implicit config: Configuration) extends QueryBuilder {

  import SearchConstants._
  import SolrQueryBuilder._

  /**
   * Look up boost values from configuration for default query fields.
   */
  private lazy val queryFieldsWithBoost: Seq[(String,Option[Double])] = Seq(
    ITEM_ID, IDENTIFIER, NAME_EXACT, NAME_MATCH, OTHER_NAMES, PARALLEL_NAMES, ALT_NAMES, NAME_SORT, TEXT
  ).map(f => f -> config.getDouble(s"search.boost.$f"))

  private lazy val spellcheckConfig: Seq[(String,Option[String])] = Seq(
    "count", "onlyMorePopular", "extendedResults", "accuracy",
    "collate", "maxCollations", "maxCollationTries", "maxResultsForSuggest"
  ).map(f => f -> config.getString(s"search.spellcheck.$f"))


  private def entityFilterParams(entities: Seq[EntityType.Value]): Seq[(String, String)] = {
    if (entities.nonEmpty) {
      val filter = entities.map(_.toString).mkString(" OR ")
      Seq("fq" -> s"$TYPE:($filter)")
    } else Seq.empty
  }

  private def accessFilterParams(userOpt: Option[UserProfile]): Seq[(String, String)] = {
    // Filter docs based on access. If the user is empty, only allow
    // through those which have accessibleTo:ALLUSERS.
    // If we have a user and they're not admin, add a filter against
    // all their groups.
    if (userOpt.isEmpty) {
      Seq("fq" -> s"$ACCESSOR_FIELD:$ACCESSOR_ALL_PLACEHOLDER")
    } else if (!userOpt.exists(_.isAdmin)) {
      // Create a boolean or query starting with the ALL placeholder, which
      // includes all the groups the user belongs to, included inherited ones,
      // i.e. accessibleTo:(ALLUSERS OR mike OR admin)
      val accessors = ACCESSOR_ALL_PLACEHOLDER +: userOpt.map(
          u => (u.id +: u.allGroups.map(_.id)).distinct).getOrElse(Nil)
      Seq("fq" -> s"$ACCESSOR_FIELD:(${accessors.mkString(" ")})")
    } else Seq.empty
  }

  private def facetFilterParams(appliedFacets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]]): Seq[(String, String)] = {
    Seq(
      "facet" -> true.toString,
      "facet.mincount" -> 1.toString
    ) ++
      allFacets.flatMap(SolrFacetParser.facetAsParams) ++
      facetFilters(allFacets, appliedFacets)
  }

  private def extraFilterParams(filters: Seq[(String, Any)]): Seq[(String, String)] = {
    filters.map { case (key, value) =>
      val filter = value match {
        // Have to quote strings
        case s: String => key + ":\"" + value + "\""
        // not value means the key is a query!
        case Unit => key
        case _ => s"$key:$value"
      }
      "fq" -> filter
    }
  }

  private def groupParams: Seq[(String, String)] = {
    // Group results by item id (as opposed to description id). Facet counts
    // are also set to reflect grouping as opposed to the number of individual
    // items.
    Seq(
      "group" -> true.toString,
      "group.field" -> ITEM_ID,
      "group.facet" -> true.toString,
      "group.ngroups" -> true.toString,
      "group.cache.percent" -> 0.toString,
      "group.offset" -> 0.toString,
      "group.limit" -> 1.toString,
      "group.format" -> "simple"
    )
  }

  private def excludeFilterParams(ids: Seq[String]): Seq[(String, String)] = {
    if (ids.nonEmpty) {
      Seq("fq" -> s"$ITEM_ID:(${ids.map(id => s"-$id").mkString(" ")})")
    } else Seq.empty
  }

  private def idFilterParams(ids: Seq[String]): Seq[(String, String)] = {
    if (ids.nonEmpty) {
      Seq("fq" -> s"$ITEM_ID:(${ids.mkString(" ")})")
    } else Seq.empty
  }

  private def basicParams(queryString: String, paging: PageParams): Seq[(String, String)] = Seq(
    "q" -> queryString,
    "wt" -> "json",
    "start" -> paging.offset.toString,
    "rows" -> paging.limit.toString,
    "debugQuery" -> debugQuery.toString,
    "defType" -> "edismax"
  )

  private def highlightParams(hasQuery: Boolean): Seq[(String, String)] = {
    // Highlight, but only if we have a query...
    if (hasQuery) Seq(
      "hl" -> true.toString,
      "hl.usePhraseHighlighter" -> true.toString,
      "hl.simple.pre" -> "<em class='highlight'>",
      "hl.simple.post" -> "</em>"
    ) else Seq.empty
  }

  private def fieldParams(fields: Seq[SearchField.Value]): Seq[(String, String)] = {
    // Apply search to specific fields. Can't find a way to do this using
    // Scalikesolr's built-in classes so we have to use it's extension-param
    // facility
    val basic = if(fields.nonEmpty) {
      Seq("qf" -> query.params.fields.mkString(" "))
    } else {
      val qfFields: String = queryFieldsWithBoost.map { case (key, boostOpt) =>
        boostOpt.map(b => s"$key^$b").getOrElse(key)
      }.mkString(" ")
      Logger.trace(s"Query fields: $qfFields")
      Seq("qf" -> qfFields)
    }

    // Set field aliases
    val aliases = for {
      config <- config.getConfig("search.fieldAliases").toSeq
      alias <- config.keys.toSeq
      fieldName <- config.getString(alias).toSeq
    } yield s"f.$alias.qf" -> fieldName

    basic ++ aliases
  }

  private def spellcheckParams: Seq[(String, String)] = {
    Seq("spellcheck" -> true.toString) ++
    spellcheckConfig.collect { case (key, Some(value)) =>
      s"spellcheck.$key" -> value
    }
  }

  private def sortParams(sort: Option[SearchSort.Value]): Seq[(String, String)] =
    sort.map { sort => "sort" -> sort.toString.split("""\.""").mkString(" ")}.toSeq

  /**
    * Run a simple filter on the name_ngram field of all entities
    * of a given type.
    */
  override def simpleFilterQuery(alphabetical: Boolean = false): Seq[(String, String)] = {

    val searchFilters = query.params.filters.filter(_.contains(":")).map(f => " +" + f).mkString
    val excludeIds = query.params.excludes.toList.flatten.map(id => s" -$ITEM_ID:$id").mkString
    val queryString = query.params.query.getOrElse("*").trim + excludeIds + searchFilters

    Seq(
      basicParams(queryString, query.paging),
      entityFilterParams(query.params.entities),
      accessFilterParams(query.user),
      idFilterParams(query.withinIds),
      groupParams,
      Seq("qf" -> s"$NAME_MATCH^2.0 $NAME_NGRAM"),
      Seq("fl" -> s"$ID $ITEM_ID $NAME_EXACT $TYPE $HOLDER_NAME $DB_ID"),
      if (alphabetical) Seq("sort" -> s"$NAME_SORT asc") else Seq.empty,
      query.extraParams.map(kp => kp._1 -> kp._2.toString).toSeq
    ).flatten
  }

  /**
   * Build a query given a set of search parameters.
   */
  override def searchQuery(): Seq[(String, String)] = {

    val searchFilters = query.params.filters.filter(_.contains(":")).map(f => " +" + f).mkString

    val defaultQuery = query.mode match {
      case SearchMode.DefaultAll => "*"
      case _ => "PLACEHOLDER_QUERY_RETURNS_NO_RESULTS" // FIXME! This sucks
    }

    // Child count to boost results seems to have an odd affect in making the
    // query only work on the default field - disabled for now...
    val queryString =
        //s"{!boost b=$CHILD_COUNT}" +
      query.params.query.getOrElse(defaultQuery).trim + searchFilters

    Seq(
      basicParams(queryString, query.paging),
      groupParams,
      fieldParams(query.params.fields),
      sortParams(query.params.sort),
      facetFilterParams(query.appliedFacets, query.facetClasses),
      entityFilterParams(query.params.entities),
      accessFilterParams(query.user),
      idFilterParams(query.withinIds),
      excludeFilterParams(query.params.excludes),
      extraFilterParams(query.filters.toSeq),
      query.extraParams.map(kp => kp._1 -> kp._2.toString).toSeq,
      highlightParams(query.params.query.isDefined),
      spellcheckParams
    ).flatten
  }
}

