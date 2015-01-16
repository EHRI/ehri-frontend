package solr

import scala.xml.{Node, Elem}
import defines.EntityType
import utils.search._
import utils.search.QueryResponse
import utils.search.SearchHit

/**
 * Helper class for parsing a Solr XML Response.
 * @param response The XML response from Solr
 */
case class SolrXmlQueryResponse(response: Elem) extends QueryResponse {

  import SearchConstants._

  lazy val phrases: Seq[String] = (response \ "lst" \ "str").filter(hasAttr("name", "q")).map(_.text)

  /**
   * Fetch the search description items returned in this response.
   */
  lazy val items: Seq[SearchHit] = (response \ "lst" \ "lst" \ "result" \ "doc").map { doc =>
    val id = (doc \ "str").filter(hasAttr("name", ID)).text
    val itemId = (doc \ "str").filter(hasAttr("name", ITEM_ID)).text
    val entityType = EntityType.withName((doc \ "str").filter(hasAttr("name", TYPE)).text.trim)
    val gid = (doc \ "long").filter(hasAttr("name", DB_ID)).text.toLong
    val highlights: Map[String,Seq[String]] = highlightMap.getOrElse(id, Map.empty)

    val parent: String = (doc \ "arr").filter(hasAttr("name", HOLDER_NAME)).text

    val fields = ((for {
      strn <- (doc \ "str").filter(hasAttr("name"))
      attrs <- strn.attributes.get("name")
      name <- attrs.headOption
    } yield name.text -> strn.text) ++ Seq(HOLDER_NAME -> parent)).toMap

    SearchHit(
      id = id,
      itemId = itemId,
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
  lazy val spellcheckSuggestion: Option[(String,String)] = rawSpellcheckSuggestions
    .sortBy(s => s._3).reverse.headOption.map(s => s._1 -> s._2)

  private def rawSpellcheckSuggestions: Seq[(String,String,Int)] = for {
    suggestions <- (response \ "lst" \ "lst").filter(hasAttr("name", "suggestions"))
    name <- suggestions \ "lst" \ "@name"
    words <- suggestions \ "lst" \ "arr" \ "lst"
    word <- words \ "str"
    freq <- words \ "int"
  } yield (name.text, word.text, freq.text.toInt)

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
    allFacets.flatMap {
      case ffc: FieldFacetClass => List(extractFieldFacet(ffc, appliedFacets))
      case qfc: QueryFacetClass => List(extractQueryFacet(qfc, appliedFacets))
    }
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
  private def extractFieldFacet(fc: FieldFacetClass, appliedFacets: List[AppliedFacet]): FieldFacetClass = {
    val applied: List[String] = appliedFacets.find(_.name == fc.key).map(_.values).getOrElse(List.empty[String])
    val nodeOpt = response.descendant.find(n => (n \ "@name").text == "facet_fields")
    val facets = nodeOpt.toList.flatMap { node =>
      val children = node.descendant.filter(n => (n \ "@name").text == fc.key)
      children.flatMap(_.descendant).flatMap { c =>
        val nameNode = c \ "@name"
        if (nameNode.length == 0) Nil
        else
           List(FieldFacet(
              nameNode.text, None,
              c.text.toInt, applied.contains(nameNode.text)))
      }
    }

    fc.copy(facets = facets)
  }

  /**
   * Extract query facets from Solr XML response.
   */
  private def extractQueryFacet(fc: QueryFacetClass, appliedFacets: List[AppliedFacet], tags: List[String] = Nil): QueryFacetClass = {
    val applied: List[String] = appliedFacets.find(_.name == fc.key).map(_.values).getOrElse(List.empty[String])
    val facets = fc.facets.flatMap{ f =>
      val nameValue = s"${SolrFacetParser.fullKey(fc)}:${SolrFacetParser.facetValue(f)}"
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
