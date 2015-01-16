package utils

import defines.EnumUtils

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object search {

  type FacetClassList = List[FacetClass[Facet]]

  object FacetQuerySort extends Enumeration {
    val Name = Value("name")
    val Count = Value("count")

    implicit val format = EnumUtils.enumFormat(FacetQuerySort)
  }

  def pathWithoutFacet[F <: Facet, FC <: FacetClass[F]](fc: FC, f: F, path: String, qs: Map[String, Seq[String]]): String = {
    joinPath(path, qs.collect {
        case (q, vals) if q == fc.param => q -> vals.filter(_ != f.value)
        case pair => pair
    })
  }

  def pathWithFacet[F <: Facet, FC <: FacetClass[F]](fc: FC, f: F, path: String, qs: Map[String, Seq[String]]): String = {
    joinPath(path, if (qs.contains(fc.param)) {
      qs.collect {
        case (q, vals) if q == fc.param => q -> vals.union(Seq(f.value)).distinct.sorted
        case pair => pair
      }
    } else qs.updated(fc.param, Seq(f.value)))
  }
}
