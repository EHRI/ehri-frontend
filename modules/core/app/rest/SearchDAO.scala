package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile
import play.api.libs.json.{Reads, Json}
import models.json.RestReadable
import models.base.AnyModel

case class SearchDAO(userProfile: Option[UserProfile]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/entities".format(host, port, mount)

  def get[MT](id: String)(implicit rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*)
      .get.map { response =>
      checkErrorAndParse(response)(rd.restReads)
    }
  }

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