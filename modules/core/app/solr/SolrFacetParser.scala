package solr

import com.github.seratch.scalikesolr.request.query.facet.{Param, Value, FacetParam}
import play.api.Logger
import utils.search._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
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

  def facetAsParams(fc: FacetClass[_]): Seq[FacetParam] = fc match {
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
}
