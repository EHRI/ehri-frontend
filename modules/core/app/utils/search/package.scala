package utils

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object search {

  type FacetClassList = List[FacetClass[Facet]]


  private def joinPath(path: String, qs: Map[String, Seq[String]]): String = {
    List(path, joinQueryString(qs)).filterNot(_=="").mkString("?")
  }

  private def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map { case (key, vals) => {
      vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))
    }}.flatten.mkString("&")
  }

  def pathWithoutFacet[F <: Facet, FC <: FacetClass[F]](fc: FC, f: F, path: String, qs: Map[String, Seq[String]]): String = {
    joinPath(path, qs.map(qv => {
      qv._1 match {
        case fc.param => (qv._1, qv._2.filter(_!=f.param))
        case _ => qv
      }
    }))
  }

  def pathWithFacet[F <: Facet, FC <: FacetClass[F]](fc: FC, f: F, path: String, qs: Map[String, Seq[String]]): String = {
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
