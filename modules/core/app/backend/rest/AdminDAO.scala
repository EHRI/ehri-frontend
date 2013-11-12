package backend.rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import models.UserProfile
import backend.{EventHandler, ApiUser}

case class AdminDAO(eventHandler: EventHandler) extends RestDAO {
  def requestUrl = "http://%s:%d/%s/admin".format(host, port, mount)

  def createNewUserProfile(implicit apiUser: ApiUser = ApiUser()): Future[UserProfile] = {
    userCall(enc(requestUrl, "createDefaultUserProfile")).post("").map { response =>
      val item = checkErrorAndParse(response)(UserProfile.Converter.restReads)
      eventHandler.handleCreate(item.id)
      item
    }
  }
}