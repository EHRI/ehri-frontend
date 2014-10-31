package utils.search

import com.github.seratch.scalikesolr.WriterType
import play.api.libs.ws.WSResponse
import play.api.libs.json.JsValue
import scala.xml.Elem

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait ResponseParser {
  def apply(response: WSResponse): QueryResponse
  def writerType: WriterType
}

trait ResponseExtractor[T] {
  def extract(response: WSResponse): T
}

object JsonResponseExtractor extends ResponseExtractor[JsValue] {
  def extract(response: WSResponse): JsValue = response.json
}

object XmlResponseExtractor extends ResponseExtractor[Elem] {
  def extract(response: WSResponse): Elem = response.xml
}

trait WSParser {
  def parse(r: WSResponse): QueryResponse
}
