package eu.ehri.project.search.solr

import javax.inject.Inject

import utils.search._
import defines.EntityType
import utils.search.SearchHit

/**
 * Extracts useful data from a Solr JSON response.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class JsonResponseHandler @Inject()(app: play.api.Application) extends ResponseHandler {
  import SearchConstants._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  // Intermediate structures...
  private case class SolrData(
    count: Int,
    rawDocs: Seq[JsObject],
    highLights: Option[Map[String,Map[String,Seq[String]]]],
    rawFacets: Map[String,Map[String,JsValue]]
  )

  private object SolrData {
    implicit val reads: Reads[SolrData] = (
      (__ \ "grouped" \ ITEM_ID \ "ngroups").read[Int] and
      (__ \ "grouped" \ ITEM_ID \ "doclist" \ "docs").read[Seq[JsObject]] and
      (__ \ "highlighting").readNullable[Map[String,Map[String,Seq[String]]]] and
      (__ \ "facet_counts").readNullable[Map[String,Map[String,JsValue]]].map(_.getOrElse(Map.empty))
    )(SolrData.apply _)
  }

  private def hitBuilder(id: String, itemId: String, entityType: EntityType.Value, gid: Long): SearchHit =
    new SearchHit(id, itemId, entityType, gid)

  private def hitReads: Reads[SearchHit] = (
    (__ \ ID).read[String] and
    (__ \ ITEM_ID).read[String] and
    (__ \ TYPE).read[EntityType.Value] and
    (__ \ DB_ID).read[Long]
  )(hitBuilder _)



  override def getResponseParser(responseBody: String): QueryResponseExtractor = new QueryResponseExtractor {

    private lazy val response: JsValue = Json.parse(responseBody)

    /**
     * Fetch the first available spellcheck suggestion.
     */
    lazy val spellcheckSuggestion: Option[(String, String)] = for {
      (word, suggests) <- rawSpellcheckSuggestions
      best <- suggests.sortBy(_.freq).reverse.headOption
    } yield (word, best.word)


    private def rawSpellcheckSuggestions: Option[(String,Seq[Suggestion])] = for {
      suggest <- (response \ "spellcheck"\ "suggestions").asOpt[Seq[JsValue]] if suggest.size > 2
      word <- suggest(0).asOpt[String]
      correct <- (suggest(1) \ "suggestion").asOpt(Reads.seq(Suggestion.suggestionReads))
    } yield (word, correct)

    /**
     * Extract query phrases from the 'q' parameter.
     */
    lazy val phrases: Seq[String] = (response \ "responseHeader" \ "params" \ "q").asOpt[String].toSeq

    /**
     * Extract items, along with their highlighting snippets.
     */
    lazy val items: Seq[SearchHit] = raw.rawDocs.flatMap { jsObj =>
      jsObj.validate(hitReads).asOpt.map { hit =>
        val highlights: Map[String,Seq[String]] = (for {
          hl <- raw.highLights
          fhl <- hl.get(hit.id)
        } yield fhl).getOrElse(Map.empty)

        val fields = jsObj.value.collect {
          case (field, JsString(str)) => field -> str
          case (field, JsArray(JsString(str) :: _)) => field -> str
        }.toMap

        hit.copy(fields = fields, highlights = highlights, phrases = phrases)
      }
    }

    /**
     * Get number of items.
     */
    def count: Int = raw.count

    /**
     * Get highlight map.
     */
    def highlightMap: Map[String, Map[String, Seq[String]]] = raw.highLights.getOrElse(Map.empty)

    private case class Suggestion(word: String, freq: Int)
    private object Suggestion {
      implicit val suggestionReads: Reads[Suggestion] = Json.reads[Suggestion]
    }

    private lazy val raw: SolrData = response.validate[SolrData].fold(
      err => throw response.as[SolrServerError],
      data => data
    )

    private def rawFieldFacets: Map[String,JsValue] = raw.rawFacets.getOrElse("facet_fields", Map.empty)
    private def rawQueryFacets: Map[String,JsValue] = raw.rawFacets.getOrElse("facet_queries", Map.empty)

    private val fieldFacetValueReader: Reads[Seq[(String,Int)]] = {
      JsPath.read[List[JsValue]].map { list =>
        list.grouped(2).collect {
          case JsString(item) :: JsNumber(count) :: Nil => item -> count.toIntExact
        }.toSeq
      }
    }

    private def appliedFacetValues(fc: FacetClass[_], appliedFacets: Seq[AppliedFacet]): Seq[String]
    = appliedFacets.find(_.name == fc.key).map(_.values).getOrElse(Seq.empty)

    private def extractFieldFacet(fc: FieldFacetClass, applied: Seq[String]): FieldFacetClass = {
      rawFieldFacets.get(fc.key).map(_.validate(fieldFacetValueReader)).collect {
        case JsSuccess(fields, path) =>
          val facets = fields.map { case (text, count) =>
            FieldFacet(text, None, count, applied.contains(text))
          }
          fc.copy(facets = facets)
      }.getOrElse(fc)
    }

    private def extractQueryFacet(fc: QueryFacetClass, applied: Seq[String]): QueryFacetClass = {
      val facetsWithCount: Seq[QueryFacet] = fc.facets.flatMap { qf =>
        val nameValue = s"${SolrFacetParser.fullKey(fc)}:${SolrFacetParser.facetValue(qf)}"
        rawQueryFacets.get(nameValue).map { v =>
          qf.copy(count = v.as[Int], applied = applied.contains(qf.value))
        }
      }
      fc.copy(facets = facetsWithCount.toList)
    }

    override def extractFacetData(appliedFacets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]]): Seq[FacetClass[Facet]] = {
      allFacets.flatMap {
        case ffc: FieldFacetClass => Some(extractFieldFacet(ffc, appliedFacetValues(ffc, appliedFacets)))
        case qfc: QueryFacetClass => Some(extractQueryFacet(qfc, appliedFacetValues(qfc, appliedFacets)))
      }
    }
  }
}
