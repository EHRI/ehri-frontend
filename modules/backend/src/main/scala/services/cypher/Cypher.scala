package services.cypher

import akka.stream.scaladsl.Source
import play.api.libs.json.{JsValue, Reads}
import play.api.libs.ws.WSResponse

import scala.concurrent.Future

trait Cypher {
  def cypher(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[JsValue]

  def get[T: Reads](scriptBody: String, params: Map[String,JsValue]): Future[T]

  def rows(scriptBody: String, params: Map[String,JsValue]): Future[Source[Seq[JsValue], _]]

  def raw(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[WSResponse]
}
