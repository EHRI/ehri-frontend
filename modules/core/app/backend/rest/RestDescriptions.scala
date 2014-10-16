package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import defines.EntityType
import play.api.Play.current
import play.api.cache.Cache
import backend._
import backend.ApiUser


/**
 * Data Access Object for managing descriptions on entities.
 */
trait RestDescriptions extends RestDAO with Descriptions {

  val eventHandler: EventHandler

  private def requestUrl = s"http://$host:$port/$mount/description"

  def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, rs: BackendResource[MT], fmt: BackendWriteable[DT], rd: backend.BackendReadable[DT], executionContext: ExecutionContext): Future[DT] = {
    val url: String = enc(requestUrl, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(fmt.restFormat)).map { response =>
      val desc: DT = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(id)
      desc
    }
  }

  def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, rs: BackendResource[MT], fmt: BackendWriteable[DT], rd: backend.BackendReadable[DT], executionContext: ExecutionContext): Future[DT] = {
    val url: String = enc(requestUrl, id, did)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .put(Json.toJson(item)(fmt.restFormat)).map { response =>
      val desc: DT = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(id)
      desc
    }
  }

  def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Unit] = {
    userCall(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg): _*)
          .delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(did)
      Cache.remove(id)
    }
  }

  def createAccessPoint[DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, fmt: BackendWriteable[DT], executionContext: ExecutionContext): Future[DT] = {
    val url: String = enc(requestUrl, id, did, EntityType.AccessPoint)
    userCall(url)
        .withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(fmt.restFormat)).map { response =>
      eventHandler.handleUpdate(id)
      Cache.remove(id)
      checkErrorAndParse(response, context = Some(url))(fmt.restFormat)
    }
  }

  def deleteAccessPoint(id: String, did: String, apid: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    val url = enc(requestUrl, id, did, EntityType.AccessPoint, apid)
    userCall(url).withHeaders(msgHeader(logMsg): _*).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
    }
  }
}


case class DescriptionDAO(eventHandler: EventHandler) extends RestDescriptions