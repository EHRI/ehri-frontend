package backend.rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import models.UserProfile
import backend.{EventHandler, ApiUser}
import play.api.libs.json.Json

case class AdminDAO(eventHandler: EventHandler) extends RestDAO {
  def requestUrl = s"$baseUrl/admin"

  def createNewUserProfile(data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty)(implicit apiUser: ApiUser = ApiUser()): Future[UserProfile] = {
    userCall(enc(requestUrl, "createDefaultUserProfile"))
        .withQueryString(groups.map(group => Constants.GROUP_PARAM -> group): _*)
        .post(Json.toJson(data)).map { response =>
      val item = checkErrorAndParse(response)(UserProfile.Converter.restReads)
      eventHandler.handleCreate(item.id)
      item
    }
  }
}