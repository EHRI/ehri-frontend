package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfileMeta
import play.api.libs.json.{Reads, Json}
import models.json.RestReadable

case class SearchDAO(userProfile: Option[UserProfileMeta]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/entities".format(host, port, mount)

  def list[MT](ids: Seq[String])(implicit rd: RestReadable[MT]): Future[Either[RestError, List[MT]]] = {
    WS.url(requestUrl).withHeaders(authHeaders.toSeq: _*)
      .post(Json.toJson(ids)).map { response =>
        checkError(response).right.map { r =>
          r.json.validate[List[MT]](Reads.list(rd.restReads)).fold(
            valid = { list => list },
            invalid = { e =>
              sys.error("Unable to decode list result: " + e.toString)
            }
          )
        }
      }
  }
}