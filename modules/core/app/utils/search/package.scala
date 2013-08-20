package utils

import play.api.libs.json.Json

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object search {

  type FacetClassList = List[FacetClass[Facet]]

  /**
   * A facet that has been "applied", i.e. a name of the field
   * and the set of values that should be used to constrain
   * a particular search.
   * @param name
   * @param values
   */
  case class AppliedFacet(name: String, values: List[String])

  object AppliedFacet {
    implicit val appliedFacetWrites = Json.writes[AppliedFacet]
  }


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
        case fc.param => (qv._1, qv._2.filter(_!=f.value))
        case _ => qv
      }
    }))
  }

  def pathWithFacet[F <: Facet, FC <: FacetClass[F]](fc: FC, f: F, path: String, qs: Map[String, Seq[String]]): String = {
    joinPath(path, if (qs.contains(fc.param)) {
      qs.map(qv => {
        qv._1 match {
          case fc.param => (qv._1, qv._2.union(Seq(f.value)).distinct)
          case _ => qv
        }
      })
    } else qs.updated(fc.param, Seq(f.value))
    )
  }

}
