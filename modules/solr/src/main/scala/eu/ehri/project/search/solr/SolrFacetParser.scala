package eu.ehri.project.search.solr

import play.api.libs.json.{JsNull, JsObject, Json}
import services.search._

private[solr] object SolrFacetParser {

  def facetValue(q: Facet): String = q match {
    case FieldFacet(value, l_, _, _) => value
    case QueryFacet(value, _, _, range, _) => range match {
      case r: QueryRange => r.points.toList match {
        case Start :: Val(p) :: Nil => s"[* TO $p]"
        case Val(p1) :: Val(p2) :: Nil => s"[$p1 TO $p2]"
        case Val(p) :: End :: Nil => s"[$p TO *]"
        case Val(p) :: Nil => p.toString
        case p => "*"
      }
      case Start => "*"
      case End => "*"
      case Val(p) => p.toString
    }
  }

  def fullKey(fc: FacetClass[_]): String =
    if (fc.multiSelect) s"{!ex=${fc.key}}${fc.key}" else fc.key

  def facetAsParams(fc: FacetClass[_]): Seq[(String, String)] = {
    val params = fc match {
      case ffc: FieldFacetClass => Seq("facet.field" -> fullKey(ffc))
      case qfc: QueryFacetClass => qfc.facets.map(p =>
        "facet.query" -> s"${fullKey(qfc)}:${facetValue(p)}")
    }

    // If the facet class is sort by name (other than the default,
    // by count) add an extra parameter to specify this.
    val sortOpt = if (fc.sort == FacetSort.Name) {
      Seq(s"f.${fc.key}.facet.sort" -> "index")
    } else Seq.empty

    val countOpt = fc.minCount.map { mc =>
      s"f.${fc.key}.facet.mincount" -> mc.toString
    }

    val limitOpt = fc.limit.map { lm =>
      s"f.${fc.key}.facet.limit" -> lm.toString
    }

    params ++ sortOpt ++ countOpt.toSeq ++ limitOpt.toSeq
  }

  def facetAsJson(fc: FacetClass[_]): Seq[(String, JsObject)] = fc match {
    case ffc: FieldFacetClass => Seq(ffc.param -> Json.obj(
      "type" -> "terms",
      "numBuckets" -> true,
      "field" -> fc.key,
      "offset" -> Json.toJson(fc.offset.getOrElse(0)),
      "limit" -> Json.toJson(fc.limit.getOrElse(100)),
      "domain" -> (if(fc.multiSelect) Json.obj("excludeTags" -> fc.key) else JsNull),
      "facet" -> Json.obj("grouped_count" -> "unique(itemId)"),
      "sort" -> (if (fc.sort == FacetSort.Name) "index" else "grouped_count")
    ))
    case fc: QueryFacetClass => fc.facets.map { fv =>
      val nameValue = s"${SolrFacetParser.fullKey(fc)}:${SolrFacetParser.facetValue(fv)}"
      nameValue -> Json.obj(
        "type" -> "query",
        "facet" -> Json.obj("grouped_count" -> "unique(itemId)"),
        "q" -> s"${fc.key}:${SolrFacetParser.facetValue(fv)}"
      )
    }
  }
}
