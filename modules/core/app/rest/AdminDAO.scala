package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile

case class AdminDAO() extends RestDAO {
  def requestUrl = "http://%s:%d/%s/admin".format(host, port, mount)

  def createNewUserProfile(implicit apiUser: ApiUser = ApiUser()): Future[UserProfile] = {
    userCall(enc(requestUrl, "createDefaultUserProfile")).post("").map { response =>
      checkErrorAndParse(response)(UserProfile.Converter.restReads)
    }
  }
}