package eu.ehri.project.search.solr

import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
  * Representation of a JSON Solr error response, which contains an `error` object
  * with `code` and `msg` fields describing a problem.
  *
  * @param code the status code, e.g. 400 for malformed parameters
  * @param msg  a description of the error
  */
case class SolrServerError(code: Int, msg: String) extends RuntimeException(msg)

private object SolrServerError {
  implicit val _reads: Reads[SolrServerError] = (
    (__ \ "error" \ "code").read[Int] and
    (__ \ "error" \ "msg").read[String]
  )(SolrServerError.apply _)
}
