package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import backend.{BackendReadable, WithId, EventHandler, ApiUser}
import play.api.libs.json.Json

case class AdminDAO(eventHandler: EventHandler) extends RestDAO {
  def requestUrl = s"http://$host:$port/$mount/admin"

  def createNewUserProfile[T <: WithId](data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty)(implicit apiUser: ApiUser, rd: BackendReadable[T], executionContext: ExecutionContext): Future[T] = {
    userCall(enc(requestUrl, "createDefaultUserProfile"))
        .withQueryString(groups.map(group => Constants.GROUP_PARAM -> group): _*)
        .post(Json.toJson(data)).map { response =>
      val item = checkErrorAndParse(response)(rd.restReads)
      eventHandler.handleCreate(item.id)
      item
    }
  }
}