package backend.rest.cypher

import scala.concurrent.Future
import play.api.PlayException
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Json,JsValue}
import play.api.libs.json.Reads
import play.api.libs.json.__
import play.api.libs.ws.Response
import play.api.libs.ws.WS
import backend.rest.RestDAO

case class CypherError(
  message: String, exception: String, stacktrace: List[String]
) extends PlayException("Cypher Script Error: %s".format(exception), message)

object CypherErrorReader {

  import play.api.libs.json.Reads._
  import play.api.libs.functional.syntax._

  implicit val cypherErrorReads: Reads[CypherError] = (
    (__ \ "message").read[String] and
    (__ \ "exception").read[String] and
    (__ \ "stacktrace").lazyRead(list[String])
  )(CypherError)
}

object CypherDAO {
  
}

case class CypherDAO() extends RestDAO {

  def requestUrl = "http://%s:%d/db/data/cypher".format(host, port)

  import CypherErrorReader._

  def checkCypherError(r: Response): JsValue = r.json.validate[CypherError].fold(
    valid = err => throw err,
    invalid = e => r.json
  )

  def cypher(scriptBody: String, params: Map[String,JsValue] = Map()): Future[JsValue] = {
    val data = Json.obj("query" -> scriptBody, "params" -> params)
    WS.url(requestUrl).withHeaders(headers.toList: _*).post(data).map(checkCypherError)
  }  
}
