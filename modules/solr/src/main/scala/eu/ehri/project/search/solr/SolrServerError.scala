package eu.ehri.project.search.solr

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class SolrServerError(code: Int, msg: String) extends RuntimeException(msg)

private object SolrServerError {
  implicit val _reads: Reads[SolrServerError] = (
    (__ \ "error"\ "code").read[Int] and
    (__ \ "error" \ "msg").read[String]
  )(SolrServerError.apply _)
}
