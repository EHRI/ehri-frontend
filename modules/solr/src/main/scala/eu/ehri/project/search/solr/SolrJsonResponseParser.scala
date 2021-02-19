package eu.ehri.project.search.solr

import models.EntityType

import javax.inject.Inject
import play.api.PlayException
import services.search.{SearchHit, _}



/**
  * Extracts useful data from a Solr JSON response.
  */
case class SolrJsonResponseParser @Inject()(config: play.api.Configuration) extends ResponseParser {

  import SearchConstants._
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  private val logger = play.api.Logger(getClass)
  private val jsonFacets = config.getOptional[Boolean]("search.jsonFacets").getOrElse(false)

  // Intermediate structures...
  private case class SolrData(
    query: Option[String],
    count: Int,
    rawDocs: Seq[JsObject],
    highLights: Option[Map[String, Map[String, Seq[String]]]],
    spellcheck: JsObject,
    rawFacets: Map[String, Map[String, JsValue]],
    facetInfo: Map[String, JsValue],
    debugTiming: Option[JsValue]
  )

  private implicit val solrDataReads: Reads[SolrData] = (
    (__ \ "responseHeader" \ "params" \ "q").readNullable[String] and
    (__ \ "grouped" \ "itemId" \ "ngroups").read[Int] and
    (__ \ "grouped" \ "itemId" \ "doclist" \ "docs").read[Seq[JsObject]] and
    (__ \ "highlighting").readNullable[Map[String, Map[String, Seq[String]]]] and
    (__ \ "spellcheck").readNullable[JsObject].map(_.getOrElse(Json.obj())) and
    (__ \ "facet_counts").readNullable[Map[String, Map[String, JsValue]]].map(_.getOrElse(Map.empty)) and
    (__ \ "facets").readNullable[Map[String, JsValue]].map(_.getOrElse(Map.empty[String, JsValue])) and
    (__ \ "debug" \ "timing" \ "process").readNullable[JsValue].orElse(Reads.pure(Option.empty[JsValue]))
  ) (SolrData.apply _)

  private case class Bucket(value: Option[String], count: Int)

  private implicit val bucketReads: Reads[Bucket] = (
    (__ \ "val").readNullable[String].or(
    (__ \ "val").readNullable[Int].map(_.map(_.toString))).or(
    (__ \ "val").readNullable[Boolean].map(_.map(_.toString))) and
    (__ \ "grouped_count").read[Int].orElse((__ \ "count").read[Int])
  )(Bucket.apply _)

  private case class FacetData(numBuckets: Option[Int], buckets: Seq[Bucket])

  private implicit val facetDataReads: Reads[FacetData] = Json.reads[FacetData]

  private implicit val hitReads: Reads[SearchHit] = (
    (__ \ ID).read[String] and
    (__ \ ITEM_ID).read[String] and
    (__ \ TYPE).read[EntityType.Value] and
    (__ \ DB_ID).read[Long]
  )((id, itemId, et, gid) => SearchHit(id, itemId, et, gid))

  private case class Suggestion(word: String, freq: Int)

  private implicit val suggestionReads: Reads[Suggestion] = Json.reads[Suggestion]

  private trait FacetParser {
    def extractQueryFacet(fc: QueryFacetClass, applied: Seq[String]): QueryFacetClass

    def extractFieldFacet(fc: FieldFacetClass, applied: Seq[String]): FieldFacetClass
  }

  private case class StandardFacetParser(raw: SolrData) extends FacetParser {
    private val fieldFacetValueReader: Reads[Seq[(String, Int)]] = JsPath.read[List[JsValue]]
      .map(_.grouped(2).collect {
        case JsString(item) :: JsNumber(count) :: Nil => item -> count.toIntExact
      }.toSeq)

    override def extractQueryFacet(fc: QueryFacetClass, applied: Seq[String]): QueryFacetClass = {
      val facetsWithCount: Seq[QueryFacet] = fc.facets.flatMap { qf =>
        val nameValue = s"${SolrFacetParser.fullKey(fc)}:${SolrFacetParser.facetValue(qf)}"
        raw.rawFacets.getOrElse("facet_queries", Map.empty).get(nameValue).map { v =>
          qf.copy(count = v.as[Int], applied = applied.contains(qf.value))
        }
      }
      fc.copy(facets = facetsWithCount.toList)
    }

    override def extractFieldFacet(fc: FieldFacetClass, applied: Seq[String]): FieldFacetClass = {
      raw.rawFacets.getOrElse("facet_fields", Map.empty).get(fc.key)
        .map(_.validate(fieldFacetValueReader)).collect {
        case JsSuccess(fields, path) =>
          val facets = fields.map { case (text, count) =>
            FieldFacet(text, None, count, applied.contains(text))
          }
          fc.copy(facets = facets)
      }.getOrElse(fc)
    }
  }

  private case class JsonFacetParser(raw: SolrData) extends FacetParser {
    override def extractQueryFacet(fc: QueryFacetClass, applied: Seq[String]): QueryFacetClass = {
      val facetsWithCount: Seq[QueryFacet] = fc.facets.flatMap { qf =>
        val nameValue = s"${SolrFacetParser.fullKey(fc)}:${SolrFacetParser.facetValue(qf)}"
        raw.facetInfo.get(nameValue).collect { case js: JsValue => js }.flatMap(_.asOpt[Bucket]).map { b =>
          qf.copy(count = b.count, applied = applied.contains(qf.value))
        }
      }
      fc.copy(facets = facetsWithCount.toList)
    }

    override def extractFieldFacet(fc: FieldFacetClass, applied: Seq[String]): FieldFacetClass = {
      raw.facetInfo.get(fc.param).collect {
        case js: JsObject => js.asOpt[FacetData].map { data =>
          fc.copy(facets = data.buckets.collect {
            case Bucket(Some(v), c) => FieldFacet(value = v, count = c, applied = applied.contains(v))
          }, total = data.numBuckets.getOrElse(-1))
        }.getOrElse(fc)
      }.getOrElse(fc)
    }
  }


  override def parse(responseBody: String, allFacets: Seq[FacetClass[Facet]] = Nil, appliedFacets: Seq[AppliedFacet] = Nil): QueryResult = {

    val raw: SolrData = Json.parse(responseBody).validate[SolrData].fold(
      err => throw Json.parse(responseBody).asOpt[SolrServerError]
        .getOrElse(new PlayException(s"Unexpected Solr response: ", responseBody)),
      data => {
        logger.debug(s"Timings: ${data.debugTiming}")
        data
      }
    )

    // If collation is enabled, fetch that because it gives us a new
    // search query with the mispelled terms replaced. Otherwise,
    // fetch the first mispelled term suggestion.
    def parseSpellcheckSuggestion: Option[(String, String)] = collatedSpellcheckSuggestions.orElse(for {
      (word, suggests) <- rawSpellcheckSuggestions
      best <- suggests.sortBy(_.freq).reverse.headOption
    } yield (word, best.word))

    // NB: For some reason the collated spellcheck suggestion is the 2nd
    // element of the "collations" array (with the first being the
    // word "collation").
    def collatedSpellcheckSuggestions: Option[(String, String)] = for {
      collationList <- (raw.spellcheck  \ "collations").asOpt[Seq[String]] if collationList.size > 1
      q <- raw.query
    } yield (
        // Hack! strip any local param sections in the collated query...
        q.replaceAll("^\\{![^\\}]+\\}\\s*", ""),
        collationList(1).replaceAll("^\\{![^\\}]+\\}\\s*", "")
    )

    def rawSpellcheckSuggestions: Option[(String, Seq[Suggestion])] = for {
      suggest <- (raw.spellcheck \ "suggestions").asOpt[Seq[JsValue]] if suggest.size > 2
      word <- suggest.headOption.flatMap(_.asOpt[String])
      correct <- (suggest(1) \ "suggestion").asOpt[Seq[Suggestion]]
    } yield (word, correct)

    def parseItems: Seq[SearchHit] = raw.rawDocs.flatMap { jsObj =>
      jsObj.validate[SearchHit].asOpt.map { hit =>
        val highlights: Map[String, Seq[String]] = (for {
          hl <- raw.highLights
          fhl <- hl.get(hit.id)
        } yield fhl).getOrElse(Map.empty)

        hit.copy(fields = jsObj.value.toMap, highlights = highlights, phrases = raw.query.toSeq)
      }
    }

    def appliedFacetValues(fc: FacetClass[_], appliedFacets: Seq[AppliedFacet]): Seq[String] =
      appliedFacets.find(_.name == fc.key).map(_.values).getOrElse(Seq.empty)

    def parseFacetData(appliedFacets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]]): Seq[FacetClass[Facet]] = {
      val fp: FacetParser = if (jsonFacets) JsonFacetParser(raw) else StandardFacetParser(raw)
      allFacets.flatMap {
        case ffc: FieldFacetClass => Some(fp.extractFieldFacet(ffc, appliedFacetValues(ffc, appliedFacets)))
        case qfc: QueryFacetClass => Some(fp.extractQueryFacet(qfc, appliedFacetValues(qfc, appliedFacets)))
      }
    }

    QueryResult(
      phrases = raw.query.toSeq,
      count = raw.count,
      facetInfo = raw.facetInfo,
      items = parseItems,
      highlightMap = raw.highLights.getOrElse(Map.empty),
      facets = parseFacetData(appliedFacets, allFacets),
      spellcheckSuggestion = parseSpellcheckSuggestion
    )
  }
}
