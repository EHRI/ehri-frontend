package utils

import defines.EnumUtils

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object search {

  object FacetQuerySort extends Enumeration {
    val Name = Value("name")
    val Count = Value("count")

    implicit val format = EnumUtils.enumFormat(FacetQuerySort)
  }

  def pathWithoutFacet(fc: FacetClass[_], f: String, path: String, qs: Map[String, Seq[String]]): String =
    http.joinPath(path, qs.collect {
        case (q, vals) if q == fc.param => q -> vals.filter(_ != f)
        case pair => pair
    })

  def pathWithFacet(fc: FacetClass[_], f: String, path: String, qs: Map[String, Seq[String]]): String =
    http.joinPath(path, if (qs.contains(fc.param)) {
      qs.collect {
        case (q, vals) if q == fc.param => q -> vals.union(Seq(f)).distinct.sorted
        case pair => pair
      }
    } else qs.updated(fc.param, Seq(f)))
}
