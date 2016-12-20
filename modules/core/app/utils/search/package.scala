package utils

import defines.EnumUtils
import play.api.libs.json.Format

package object search {

  object FacetQuerySort extends Enumeration {
    val Name = Value("name")
    val Count = Value("count")

    implicit val format: Format[FacetQuerySort.Value] = EnumUtils.enumFormat(FacetQuerySort)
  }

  def pathWithoutFacet(fc: FacetClass[_], f: String, path: String, qs: Map[String, Seq[String]]): String =
    http.joinPath(path, qs.collect {
      // Specific facets
      case (q, vals) if q == fc.param => q -> vals.filter(_ != f)
      // Generic facets
      case (k, vals) if k == SearchParams.FACET => k -> vals.filter(_ != s"${fc.param}:$f")
      case pair => pair
    })

  def pathWithFacet(fc: FacetClass[_], f: String, path: String, qs: Map[String, Seq[String]]): String =
    http.joinPath(path, if (qs.contains(fc.param)) {
      qs.collect {
        case (q, vals) if q == fc.param => q -> vals.union(Seq(f)).distinct.sorted
        case pair => pair
      }
    } else qs.updated(fc.param, Seq(f)))

  def pathWithGenericFacet(fc: FacetClass[_], f: String, path: String, qs: Map[String, Seq[String]]): String =
    http.joinPath(path, if (qs.contains(SearchParams.FACET)) {
      qs.collect {
        case (k, vals) if k == SearchParams.FACET => k -> vals.union(Seq(s"${fc.param}:$f")).distinct.sorted
        case pair => pair
      }
    } else qs.updated(SearchParams.FACET, Seq(s"${fc.param}:$f")))
}
