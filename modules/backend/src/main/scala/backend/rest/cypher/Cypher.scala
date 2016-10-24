package backend.rest.cypher

import play.api.libs.json.{Reads, JsValue}
import play.api.libs.ws.StreamedResponse

import scala.concurrent.Future

trait Cypher {
  def cypher(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[JsValue]

  def get[T: Reads](scriptBody: String, params: Map[String,JsValue]): Future[T]

  def stream(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[StreamedResponse]
}
