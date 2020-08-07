package utils

import org.apache.jena.iri.IRIFactory

package object http {

  import java.net.{URLDecoder, URLEncoder}

  import play.api.mvc.RequestHeader

  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH").exists(_.toUpperCase == "XMLHTTPREQUEST")

  def paramsToForm(seq: Seq[(String, String)]): Map[String, Seq[String]] =
    seq.foldLeft(Map.empty[String,Seq[String]]) { case (m, (key, vals)) =>
      m.updated(key, vals +: m.getOrElse(key, Seq.empty))
    }

  def joinPath(path: String, qs: Map[String, Seq[String]]): String =
    List(path, joinQueryString(qs)).filterNot(_=="").mkString("?")

  /**
    * Turn a seq of parameters into an URL parameter string, not including
    * the initial '?'.
    * @param qs a seq of key -> value pairs
    * @return an encoded URL parameter string
    */
  def joinQueryString(qs: Seq[(String, String)]): String =
    qs.map { case (key, value) =>
      URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
    }.mkString("&")

  /**
   * Turn a map of parameters into an URL parameter string, not including
   * the initial '?'.
   * @param qs a map of key -> value sequences
   * @return an encoded URL parameter string
   */
  def joinQueryString(qs: Map[String, Seq[String]]): String =
    joinQueryString(qs.toSeq.flatMap { case (key, vals) => vals.map(value => key -> value)})

  /**
   * Turn an URL parameter string into a map of key to value sequences.
   * @param s an encoded URL parameter string
   * @return a decoded map of key -> value sequences
   */
  def parseQueryString(s: String): Map[String,Seq[String]] =
    paramsToForm(s.substring(s.indexOf('?')).split("&").toSeq.map(_.split("=", 2))
      .map(p => URLDecoder.decode(p(0), "UTF-8") -> URLDecoder.decode(p(1), "UTF-8") ))

  /**
    * Convert a unicode IRI into an appropriately encoded URI.
    *
    * @param iri the IRI string
    * @return a URI-encoding string
    */
  def iriToUri(iri: String): String = {
    // FIXME: is toASCIIString necessary here, rather than regular toString?
    IRIFactory.iriImplementation().create(iri).toURI.toASCIIString
  }
}
