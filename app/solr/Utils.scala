package solr

import facet._

/**
 * User: mike
 *
 * Utilities for handling request URLs and Solr facets.
 */
object Utils {

  def joinPath(path: String, qs: Map[String, Seq[String]]): String = {
    List(path, joinQueryString(qs)).filterNot(_=="").mkString("?")
  }

  def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map { case (key, vals) => {
      vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))
    }}.flatten.mkString("&")
  }

  def pathWithoutFacet(fc: FacetClass, f: Facet, path: String, qs: Map[String, Seq[String]]): String = {
    joinPath(path, qs.map(qv => {
      qv._1 match {
        case fc.param => (qv._1, qv._2.filter(_!=f.param))
        case _ => qv
      }
    }))
  }

  def pathWithFacet(fc: FacetClass, f: Facet, path: String, qs: Map[String, Seq[String]]): String = {
    joinPath(path, if (qs.contains(fc.param)) {
      qs.map(qv => {
        qv._1 match {
          case fc.param => (qv._1, qv._2.union(Seq(f.param)).distinct)
          case _ => qv
        }
      })
    } else qs.updated(fc.param, Seq(f.param))
    )
  }
}

