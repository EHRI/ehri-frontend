package services.cypher

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.libs.json._

import scala.concurrent.Future


case class CypherExplain(data: JsValue) {
  def isValid: Boolean = (data \ "results" \ 0 \"plan").validate[JsObject].isSuccess
  def hasNotifications: Boolean = (data \ "notifications").asOpt[Seq[JsValue]].exists(_.nonEmpty)
  def hasErrors: Boolean = (data \ "errors").asOpt[Seq[JsValue]].exists(_.nonEmpty)
}

trait CypherService {
  /**
    * Fetch a sequence of rows from an arbitrary Cypher query.
    *
    * @param scriptBody the query body
    * @param params the query parameters
    * @return the result
    */
  def get(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[CypherResult]

  /**
    * Fetch a stream of Cypher result rows.
    *
    * @param scriptBody the query body
    * @param params the query parameters
    * @return the result rows as a stream
    */
  def rows(scriptBody: String, params: Map[String,JsValue] = Map.empty): Source[List[JsValue], _]

  /**
    * Run explain on a given query. The EXPLAIN keyword should not be included in the query.
    * @param scriptBody the query body
    * @param params the query parameters
    * @return the explain JSON output
    */
  def explain(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[CypherExplain]

  /**
    * Fetch result in legacy Cypher HTTP format.
    *
    * @param scriptBody the query body
    * @param params the query parameters
    * @return
    */
  def legacy(scriptBody: String, params: Map[String, JsValue] = Map.empty): Source[ByteString, _]
}
