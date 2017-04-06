package backend.rest.cypher

import akka.stream.scaladsl.Source
import play.api.libs.json.{JsValue, Reads}
import play.api.libs.ws.StreamedResponse

import scala.concurrent.Future

trait Cypher {
  def cypher(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[JsValue]

  def get[T: Reads](scriptBody: String, params: Map[String,JsValue]): Future[T]

  def rows(scriptBody: String, params: Map[String,JsValue]): Future[Source[Seq[JsValue], _]]

  def stream(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[StreamedResponse]
}
