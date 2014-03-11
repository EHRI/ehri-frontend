package solr

import utils.search._
import play.api.libs.json.{Json, JsValue}
import defines.EntityType
import utils.search.SearchHit
import solr.facet.{SolrQueryFacet, QueryFacetClass, SolrFieldFacet, FieldFacetClass}
import play.api.Logger
import com.github.seratch.scalikesolr.WriterType

/**
 * Extracts useful data from a Solr JSON response.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SolrJsonQueryResponse(response: JsValue) extends QueryResponse {

  import SolrConstants._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  /**
   * Fetch the first available spellcheck suggestion.
   */
  lazy val spellcheckSuggestion: Option[(String, String)] = for {
    suggest <- (response \ "spellcheck"\ "suggestions").asOpt[Seq[JsValue]] if suggest.size > 2
    word <- suggest(0).asOpt[String]
    correct <- (suggest(1) \ "suggestion").asOpt(Reads.seq(Suggestion.suggestionReads))
    first <- correct.headOption
  } yield (word, first.word)

  /**
   * Extract query phrases from the 'q' parameter.
   */
  lazy val phrases: Seq[String]
      = (response \ "responseHeader" \ "params" \ "q").asOpt[String].toSeq

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

  // Intermediate structures...

  private case class SolrData(
    count: Int,
    rawDocs: Seq[JsObject],
    highLights: Option[Map[String,Map[String,Seq[String]]]],
    rawFacets: Map[String,Map[String,JsValue]]
    )

  private object SolrData {
    implicit val reads: Reads[SolrData] = (
      (__ \ "grouped" \ ITEM_ID \ "matches").read[Int] and
        (__ \ "grouped" \ ITEM_ID \ "doclist" \ "docs").read[Seq[JsObject]] and
        (__ \ "highlighting").readNullable[Map[String,Map[String,Seq[String]]]] and
        (__ \ "facet_counts").read[Map[String,Map[String,JsValue]]]
      )(SolrData.apply _)
  }

  private def hitBuilder(id: String, itemId: String, name: String, entityType: EntityType.Value, gid: Long): SearchHit
  = new SearchHit(id, itemId, name, entityType, gid)

  private def hitReads: Reads[SearchHit] = (
    (__ \ ID).read[String] and
      (__ \ ITEM_ID).read[String] and
      (__ \ NAME_EXACT).read[String] and
      (__ \ TYPE).read[EntityType.Value] and
      (__ \ "gid").read[Long]
    )(hitBuilder _)


  private lazy val raw: SolrData = response.as[SolrData]
  private def rawFieldFacets: Map[String,JsValue] = raw.rawFacets.get("facet_fields").getOrElse(Map.empty)
  private def rawQueryFacets: Map[String,JsValue] = raw.rawFacets.get("facet_queries").getOrElse(Map.empty)

  private val fieldFacetValueReader: Reads[Seq[(String,Int)]] = {
    JsPath.read[List[JsValue]].map { list =>
      list.grouped(2).collect {
        case JsString(item) :: JsNumber(count) :: Nil => item -> count.toIntExact
      }.toSeq
    }
  }

  private def appliedFacetValues(fc: FacetClass[_], appliedFacets: Seq[AppliedFacet]): Seq[String]
    = appliedFacets.find(_.name == fc.key).map(_.values).getOrElse(Seq.empty)

  private def extractFieldFacet(fc: FieldFacetClass, applied: Seq[String]): FacetClass[Facet] = {
    rawFieldFacets.get(fc.key).map(_.validate(fieldFacetValueReader)).collect {
      case JsSuccess(fields, path) =>
        val facets = fields.map { case (text, count) =>
          SolrFieldFacet(
            text, text, None,
            count, applied.contains(text))
        }.toList
        fc.copy(facets = facets)
    }.getOrElse(fc)
  }

  private def extractQueryFacet(fc: QueryFacetClass, applied: Seq[String]): FacetClass[Facet] = {
    val facetsWithCount: List[SolrQueryFacet] = fc.facets.flatMap { qf =>
      val nameValue = s"${fc.key}:${qf.solrValue}"
      rawQueryFacets.get(nameValue).map { v =>
        qf.copy(count = v.as[Int], applied = applied.contains(qf.value))
      }
    }
    fc.copy(facets = facetsWithCount.toList)
  }

  def extractFacetData(appliedFacets: List[AppliedFacet], allFacets: utils.search.FacetClassList): utils.search.FacetClassList = {
    allFacets.flatMap {
      case ffc: FieldFacetClass => Some(extractFieldFacet(ffc, appliedFacetValues(ffc, appliedFacets)))
      case qfc: QueryFacetClass => Some(extractQueryFacet(qfc, appliedFacetValues(qfc, appliedFacets)))
      case e => {
        Logger.logger.warn("Unknown facet class type: {}", e)
        None
      }
    }
  }
}

object SolrJsonQueryResponse extends ResponseParser {
  def apply(responseString: String) = new SolrJsonQueryResponse(Json.parse(responseString))
  def writerType = WriterType.JSON
}