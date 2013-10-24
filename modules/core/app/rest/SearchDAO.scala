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

  def get[MT](id: String)(implicit rd: RestReadable[MT]): Future[MT] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(rd.restReads)
    }
  }

  def list[MT](ids: Seq[String])(implicit rd: RestReadable[MT]): Future[List[MT]] = {
    // NB: Using POST here because the list of IDs can
    // potentially overflow the GET param length...
    if (ids.isEmpty) Future.successful(List.empty[MT])
    else {
      WS.url(requestUrl).withHeaders(authHeaders.toSeq: _*).post(Json.toJson(ids)).map { response =>
        checkErrorAndParse(response)(Reads.list(rd.restReads))
      }
    }
  }
}