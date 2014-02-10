package solr

import utils.search._
import play.api.libs.json.{Json, JsValue}
import defines.EntityType
import utils.search.SearchHit
import solr.facet.{SolrQueryFacet, QueryFacetClass, SolrFieldFacet, FieldFacetClass}
import play.api.Logger

object SolrJsonQueryResponse extends ResponseParser {
  def apply(responseString: String) = new SolrJsonQueryResponse(Json.parse(responseString))
}

/**
 * Extracts useful data from a Solr JSON response.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SolrJsonQueryResponse(response: JsValue) extends QueryResponse {

  import SolrConstants._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  private case class Suggestion(word: String, freq: Int)
  private object Suggestion {
    implicit val suggestionReads: Reads[Suggestion] = Json.reads[Suggestion]
  }

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


  lazy val spellcheckSuggestion: Option[(String, String)] = for {
    suggest <- (response \ "spellcheck"\ "suggestions").asOpt[Seq[JsValue]] if suggest.size == 4
    word <- suggest.headOption.flatMap(_.asOpt[String])
    correct <- (suggest(1) \ "suggestion").asOpt(Reads.seq(Suggestion.suggestionReads))
    first <- correct.headOption
  } yield (word, first.word)

  lazy val phrases: Seq[String] = (response \ "responseHeader" \ "params" \ "q").asOpt[String].toSeq

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

  private val fieldFacetValueReader: Reads[List[(String,Int)]] = {
    __.read[List[JsValue]].map {
      list =>
        list.grouped(2).flatMap {
          case item :: count :: Nil => {
            Some((item.as[String], count.as[Int]))
          }
          case _ => Nil
        }.toList
    }
  }

  private def tagFunc(tags: List[String]): String = tags match {
    case Nil => ""
    case _ => "{!ex=" + tags.mkString(",") + "}"
  }

  private def appliedFacetValues(fc: FacetClass[_], appliedFacets: Seq[AppliedFacet]): Seq[String]
    = appliedFacets.find(_.name == fc.key).map(_.values).getOrElse(Seq.empty)

  private def extractFieldFacet(fc: FieldFacetClass, applied: Seq[String], tags: List[String] = Nil): FacetClass[Facet] = {
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

  private def extractQueryFacet(fc: QueryFacetClass, applied: Seq[String], tags: List[String] = Nil): FacetClass[Facet] = {
    val facetsWithCount: List[SolrQueryFacet] = fc.facets.flatMap { qf =>
      val nameValue = s"${tagFunc(tags)}${fc.key}:${qf.solrValue}"
      rawQueryFacets.get(nameValue).map { v =>
        qf.copy(count = v.as[Int], applied = applied.contains(qf.value))
      }
    }
    fc.copy(facets = facetsWithCount.toList)
  }

  def extractFacetData(appliedFacets: List[AppliedFacet], allFacets: utils.search.FacetClassList): utils.search.FacetClassList = {
    val tags = allFacets.filter(_.tagExclude).map(_.key)
    allFacets.flatMap {
      case ffc: FieldFacetClass => Some(extractFieldFacet(ffc, appliedFacetValues(ffc, appliedFacets), tags))
      case qfc: QueryFacetClass => Some(extractQueryFacet(qfc, appliedFacetValues(qfc, appliedFacets), tags))
      case e => {
        Logger.logger.warn("Unknown facet class type: {}", e)
        None
      }
    }
  }

  def count: Int = raw.count

  def highlightMap: Map[String, Map[String, Seq[String]]] = raw.highLights.getOrElse(Map.empty)
}
