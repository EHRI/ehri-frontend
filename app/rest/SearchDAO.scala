package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.{UserProfile, Entity}
import play.api.libs.json.Json

case class SearchDAO(userProfile: Option[UserProfile]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/entities".format(host, port, mount)

  def list(ids: Seq[String]): Future[Either[RestError, List[Entity]]] = {
    WS.url(requestUrl).withHeaders(authHeaders.toSeq: _*)
      .post(Json.toJson(ids)).map { response =>
        checkError(response).right.map { r =>
          r.json.validate[List[models.Entity]].fold(
            valid = { list => list },
            invalid = { e =>
              sys.error("Unable to decode list result: " + e.toString)
            }
          )
        }
      }
  }
}