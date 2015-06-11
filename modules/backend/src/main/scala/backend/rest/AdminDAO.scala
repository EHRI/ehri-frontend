package backend.rest

import javax.inject.Inject

import play.api.cache.CacheApi
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import backend.{Readable, WithId, EventHandler, ApiUser}
import play.api.libs.json.Json

case class AdminDAO @Inject ()(eventHandler: EventHandler, cache: CacheApi, app: play.api.Application, ws: WSClient) extends RestDAO {
  def requestUrl = s"$baseUrl/admin"

  def createNewUserProfile[T <: WithId](data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty)(implicit apiUser: ApiUser, rd: Readable[T], executionContext: ExecutionContext): Future[T] = {
    userCall(enc(requestUrl, "createDefaultUserProfile"))
        .withQueryString(groups.map(group => Constants.GROUP_PARAM -> group): _*)
        .post(Json.toJson(data)).map { response =>
      val item = checkErrorAndParse(response)(rd.restReads)
      eventHandler.handleCreate(item.id)
      item
    }
  }
}