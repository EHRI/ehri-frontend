package backend.parse

import play.api.libs.json.{Format, Json}
import play.api.Logger
import backend.rest.RestDAO
import scala.concurrent.{Future, ExecutionContext}

/**
 * DAO for interacting with the Parse REST service.
 *
 * @param objectName Name of the parse object class
 * @tparam T The type of Parse object
 */
abstract class ParseDAO[T: Format](objectName: String) extends RestDAO {

  case class Confirmation(createdAt: String, objectId: String)

  object Confirmation {
    implicit val format: Format[Confirmation] = Json.format[Confirmation]
  }

  case class Results(results: Seq[T])

  object Results {
    implicit val format: Format[Results] = Json.format[Results]
  }

  private def appKey = app.configuration.getString("parse.keys.applicationId").getOrElse("fake")

  private def restKey = app.configuration.getString("parse.keys.restApiKey").getOrElse("fake")

  private def url = app.configuration.getString("parse.baseUrl").getOrElse("fake")

  private def parseHeaders = Seq(
    "X-Parse-Application-Id" -> appKey,
    "X-Parse-REST-API-Key" -> restKey,
    "Content-type" -> "application/json"
  )


  private def parseUrl(oid: Option[String]) =
    oid.map(id => enc(url, objectName, id)).getOrElse(enc(url, objectName))

  private def parseCall(oid: Option[String] = None, params: Seq[(String,String)] = Seq.empty) =
    ws.url(parseUrl(oid)).withHeaders(parseHeaders: _*).withQueryString(params: _*)

  def create(feedback: T)(implicit executionContext: ExecutionContext): Future[String] = {
    parseCall().post(Json.toJson(feedback)).map { r =>
      Logger.info("Parse create response: " + r.body)
      r.json.as[Confirmation].objectId
    }
  }

  def list(params: (String,String)*)(implicit executionContext: ExecutionContext): Future[Seq[T]] = {
    parseCall(params = params).get().map { r =>
      r.json.as[Results].results
    }
  }

  def delete(id: String)(implicit executionContext: ExecutionContext): Future[Boolean] = {
    parseCall(Some(id)).delete().map { r =>
      Logger.info("Parse delete response: " + r.body)
      r.status >= 200 && r.status < 300
    }
  }
}

