package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfileMeta


case class AdminDAO(userProfile: Option[UserProfileMeta]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/admin".format(host, port, mount)

  def createNewUserProfile: Future[Either[RestError, UserProfileMeta]] = {
    WS.url(enc(requestUrl, "createDefaultUserProfile")).withHeaders(headers.toSeq: _*).post("").map { response =>
        checkErrorAndParse(response)(UserProfileMeta.Converter.restReads)
      }
  }
}