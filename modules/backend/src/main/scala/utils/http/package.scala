package utils

import org.apache.jena.iri.IRIFactory

import java.net.URLDecoder

package object http {

  import java.net.URLEncoder

  def joinPath(path: String, qs: Map[String, Seq[String]]): String =
    List(path, joinQueryString(qs)).filter(_.trim.nonEmpty).mkString("?")

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
    joinQueryString(qs.toSeq.flatMap { case (key, values) => values.map(value => key -> value)})

  /**
    * Convert a unicode IRI into an appropriately encoded URI.
    *
    * @param iri the IRI string
    * @return a URI-encoding string
    */
  def iriToUri(iri: String): String = {
    IRIFactory.iriImplementation().create(iri).toURI.toASCIIString
  }

  /**
    * Parse a query string into parameters.
    *
    * This only handles the &foo=bar form and should not be considered
    * a complete implementation.
    *
    * @param s a query string
    * @return a sequence of key, value pairs
    */
  def parseQueryString(s: String): Seq[(String, String)] = s.split('&')
    .map(_.split('=').toList)
    .collect { case key :: value :: Nil =>
      URLDecoder.decode(key, "UTF-8") -> URLDecoder.decode(value, "UTF-8")
    }.toSeq
}
