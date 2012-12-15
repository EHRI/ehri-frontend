package rest.cypher

import scala.concurrent.Future
import com.codahale.jerkson.Json.generate
import play.api.PlayException
import play.api.libs.concurrent.execution.defaultContext
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.Reads.StringReads
import play.api.libs.json.Reads.functorReads
import play.api.libs.json.Reads.list
import play.api.libs.json.__
import play.api.libs.json.util.functionalCanBuildApplicative
import play.api.libs.json.util.toFunctionalBuilderOps
import play.api.libs.ws.Response
import play.api.libs.ws.WS
import rest.RestDAO
import play.api.http.HeaderNames
import play.api.http.ContentTypes

case class CypherError(
  val message: String, val exception: String, val stacktrace: List[String]  
) extends PlayException("Cypher Script Error: %s".format(exception), message)

object CypherErrorReader {

  import play.api.libs.json.Reads._

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

  def checkCypherError(r: Response): Either[CypherError, JsValue] = r.json.validate[CypherError].fold(
    valid = err => Left(err),
    invalid = e => Right(r.json)
  )

  def cypher(scriptBody: String, params: Map[String,Any] = Map()): Future[Either[CypherError, JsValue]] = {
    val data = Map("query" -> scriptBody, "params" -> params)
    WS.url(requestUrl).withHeaders(headers.toList: _*).post(generate(data)).map(checkCypherError(_))
  }  
}
