package services.cypher

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json._

import scala.concurrent.Future


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
    * Fetch result in legacy Cypher HTTP format.
    *
    * @param scriptBody the query body
    * @param params the query parameters
    * @return
    */
  def legacy(scriptBody: String, params: Map[String, JsValue] = Map.empty): Source[ByteString, _]
}
