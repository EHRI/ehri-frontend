package solr

import solr.facet.{QueryFacetClass, FieldFacetClass}
import scala.xml.{Node, Elem}
import defines.EntityType
import utils.search._
import play.api.Logger
import utils.search.SearchHit
import solr.facet.FieldFacetClass
import solr.facet.QueryFacetClass
import com.github.seratch.scalikesolr._
import utils.search.QueryResponse
import utils.search.SearchHit
import solr.facet.FieldFacetClass
import solr.facet.QueryFacetClass

/**
 * User: michaelb
 */
object SolrXmlQueryResponse extends ResponseParser {
  def apply(responseString: String) = new SolrXmlQueryResponse(xml.XML.loadString(responseString))
  def writerType = WriterType.Standard
}

/**
 * Helper class for parsing a Solr XML Response.
 * @param response The XML response from Solr
 */
case class SolrXmlQueryResponse(response: Elem) extends QueryResponse {

  import SolrConstants._

  lazy val phrases: Seq[String] = (response \ "lst" \ "str").filter(hasAttr("name", "q")).map(_.text)

  /**
   * Fetch the search description items returned in this response.
   */
  lazy val items: Seq[SearchHit] = (response \ "lst" \ "lst" \ "result" \ "doc").map { doc =>
    val id = (doc \ "str").filter(hasAttr("name", ID)).text
    val itemId = (doc \ "str").filter(hasAttr("name", ITEM_ID)).text
    val name = (doc \ "str").filter(hasAttr("name", NAME_EXACT)).text
    val entityType = EntityType.withName((doc \ "str").filter(hasAttr("name", TYPE)).text.trim)
    val gid = (doc \ "long").filter(hasAttr("name", DB_ID)).text.toLong
    val highlights: Map[String,Seq[String]] = highlightMap.getOrElse(id, Map.empty)

    val fields = (for {
      strn <- (doc \ "str").filter(hasAttr("name"))
      attrs <- strn.attributes.get("name")
      name <- attrs.headOption
    } yield name.text -> strn.text).toMap

    SearchHit(
      id = id,
      itemId = itemId,
      name = name,
      `type` = entityType,
      gid = gid,
      fields = fields,
      highlights = highlights,
      phrases = phrases
    )
  }

  /**
   * Get the *first* spellcheck suggestion offered. Ultimately, more might be useful,
   * but the first is okay for now...
   */
  lazy val spellcheckSuggestion: Option[(String,String)] = {
    for {
      suggestion <- (response \ "lst" \ "lst").find(hasAttr("name", "suggestions"))
      name <- (suggestion \ "lst" \ "@name").headOption
      word <- (suggestion \ "lst" \ "arr" \ "lst" \ "str").find(hasAttr("name", "word"))
    } yield (name.text, word.text)
  }

  /**
   * Parse highlight data of the form:
   *
   * <lst name="highlighting">
   *   <lst name="item-id">
   *     <arr name="field-name">
   *       <str>Some text with <em>emphasis</em></str>
   *       ...
   *     </arr>
   */
  lazy val highlightMap: Map[String,Map[String,Seq[String]]] = highlighting.map { hl =>
    val nodes = (hl \ "lst").map { hlnode =>
      val id = (hlnode \ "@name").text
      val highlights = (hlnode \ "arr").map { arr =>
        val field = (arr \ "@name").text
        field -> (arr \ "str").map(_.text)
      }
      id -> highlights.toMap
    }
    nodes.toMap
  }.getOrElse(Map.empty)

  private def highlighting: Option[Node] = (response \ "lst").find(hasAttr("name", "highlighting"))

  /**
   * Count the number of search descriptions returned in this response.
   */
  lazy val count: Int = {
    val s = (response \ "lst" \ "lst" \ "int").filter(hasAttr("name", "ngroups")).text
    try {
      s.toInt
    } catch {
      case e: NumberFormatException => 0
    }
  }

  /**
   * Extract the facet data from this response, given a list of the facets
   * used to constrain the response, and the complete set of facet info requested.
   * @param appliedFacets Facets that were applied to the request
   * @param allFacets All relevant facets for the request
   * @return
   */
  def extractFacetData(appliedFacets: List[AppliedFacet], allFacets: FacetClassList): FacetClassList = {
    val tags = allFacets.filter(_.tagExclude).map(_.key)
    allFacets.flatMap {
      case ffc: FieldFacetClass => List(extractFieldFacet(ffc, appliedFacets, tags))
      case qfc: QueryFacetClass => List(extractQueryFacet(qfc, appliedFacets, tags))
      case e => {
        Logger.logger.warn("Unknown facet class type: {}", e)
        Nil
      }
    }
  }

  private def tagFunc(tags: List[String]): String = tags match {
    case Nil => ""
    case _ => "{!ex=" + tags.mkString(",") + "}"
  }

  /**
   * Extract field facets from XML which looks like:
   *
   * <lst name="facet_counts">
   *  <lst name="facet_queries"/>
   *   <lst name="facet_fields">
   *     <lst name="languageCode">
   *       <int name="en">697</int>
   *       <int name="de">1</int>
   *       <int name="nl">1</int>
   *   ...
   */
  private def extractFieldFacet(fc: solr.facet.FieldFacetClass, appliedFacets: List[AppliedFacet], tags: List[String] = Nil): solr.facet.FieldFacetClass = {
    val applied: List[String] = appliedFacets.find(_.name == fc.key).map(_.values).getOrElse(List.empty[String])
    val nodeOpt = response.descendant.find(n => (n \ "@name").text == "facet_fields")
    val facets = nodeOpt.toList.flatMap { node =>
      val children = node.descendant.filter(n => (n \ "@name").text == fc.key)
      children.flatMap(_.descendant).flatMap { c =>
        val nameNode = c \ "@name"
        if (nameNode.length == 0) Nil
        else
           List(solr.facet.SolrFieldFacet(
              nameNode.text, nameNode.text, None,
              c.text.toInt, applied.contains(nameNode.text)))
      }
    }

    fc.copy(facets = facets)
  }

  /**
   * Extract query facets from Solr XML response.
   */
  private def extractQueryFacet(fc: solr.facet.QueryFacetClass, appliedFacets: List[AppliedFacet], tags: List[String] = Nil): solr.facet.QueryFacetClass = {
    val applied: List[String] = appliedFacets.find(_.name == fc.key).map(_.values).getOrElse(List.empty[String])
    val facets = fc.facets.flatMap{ f =>
      var nameValue = s"${tagFunc(tags)}${fc.key}:${f.solrValue}"
      response.descendant.filter(n => (n \\ "@name").text == nameValue).text match {
        case "" => Nil
        case v => List(
          f.copy(count = v.toInt, applied = applied.contains(f.value))
        )
      }
    }

    fc.copy(facets = facets)
  }
  private def hasAttr(name: String)(node: Node): Boolean = {
    node.attributes.exists(attr => attr.key == name)
  }

  private def hasAttr(name: String, value: String)(node: Node): Boolean = {
    node.attributes.exists(attr => attr.key == name && attr.value.text == value)
  }
}
