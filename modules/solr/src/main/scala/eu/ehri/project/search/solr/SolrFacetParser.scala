package eu.ehri.project.search.solr

import com.github.seratch.scalikesolr.request.query.facet.{Param, Value, FacetParam}
import play.api.Logger
import utils.search._

object SolrFacetParser {
  def facetValue(q: Facet): String = q match {
    case FieldFacet(value, l_, _, _) => value
    case QueryFacet(value, _, _, range, _) => range match {
      case r: QueryRange => r.points.toList match {
        case Start :: Val(p) :: Nil => s"[* TO $p]"
        case Val(p1) :: Val(p2) :: Nil => s"[$p1 TO $p2]"
        case Val(p) :: End :: Nil => s"[$p TO *]"
        case Val(p) :: Nil => p.toString
        case p =>
          Logger.warn(s"Unsupported facet class points: $r -> $p")
          "*"
      }
      case Start => "*"
      case End => "*"
      case Val(p) => p.toString
    }
  }

  def fullKey(fc: FacetClass[_]): String =
    if (fc.multiSelect) s"{!ex=${fc.key}}${fc.key}" else fc.key

  def facetAsParams(fc: FacetClass[_]): Seq[FacetParam] = {
    val params = fc match {
      case ffc: FieldFacetClass => Seq(new FacetParam(
        Param("facet.field"),
        Value(fullKey(ffc))
      ))
      case qfc: QueryFacetClass => qfc.facets.map(p =>
        new FacetParam(
          Param("facet.query"),
          Value(s"${fullKey(qfc)}:${facetValue(p)}")
        )
      )
    }

    // If the facet class is sort by name (other than the default,
    // by count) add an extra parameter to specify this.
    val sortOpt = if (fc.sort == FacetSort.Name) {
      Some(new FacetParam(
        Param(s"f.${fc.key}.facet.sort"),
        Value("index")
      ))
    } else None

    val countOpt = fc.minCount.map { mc =>
      new FacetParam(
        Param(s"f.${fc.key}.facet.mincount"),
        Value(mc.toString)
      )
    }

    val limitOpt = fc.limit.map { mc =>
      new FacetParam(
        Param(s"f.${fc.key}.facet.limit"),
        Value(mc.toString)
      )
    }

    params ++ sortOpt.toSeq ++ countOpt.toSeq ++ limitOpt.toSeq
  }
}
