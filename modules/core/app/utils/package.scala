
package object utils {

  import java.net.{URLDecoder,URLEncoder}
  import play.api.mvc.RequestHeader

  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH").exists(_.toUpperCase == "XMLHTTPREQUEST")

  def joinPath(path: String, qs: Map[String, Seq[String]]): String =
    List(path, joinQueryString(qs)).filterNot(_=="").mkString("?")

  /**
   * Turn a map of parameters into an URL parameter string, not including
   * the initial '?'.
   * @param qs a map of key -> value sequences
   * @return an encoded URL parameter string
   */
  def joinQueryString(qs: Map[String, Seq[String]]): String =
    qs.map { case (key, vals) =>
      vals.map(v => "%s=%s".format(URLEncoder.encode(key, "UTF-8"), URLEncoder.encode(v, "UTF-8")))
    }.flatten.mkString("&")

  /**
   * Turn an URL parameter string into a map of key to value sequences.
   * @param s an encoded URL parameter string
   * @return a decoded map of key -> value sequences
   */
  def parseQueryString(s: String): Map[String,Seq[String]] =
    s.substring(s.indexOf('?')).split("&").map(_.split("=", 2))
      .map(p => URLDecoder.decode(p(0), "UTF-8") -> URLDecoder.decode(p(1), "UTF-8") )
      .foldLeft(Map.empty[String,Seq[String]]) { case (m, (key, vals)) =>
      m.updated(key, vals +: m.getOrElse(key, Seq.empty))
    }
}