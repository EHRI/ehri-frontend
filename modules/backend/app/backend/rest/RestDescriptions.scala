package backend.rest

import scala.concurrent.Future
import play.api.libs.json._
import defines.EntityType
import play.api.cache.Cache
import backend._


/**
 * Data Access Object for managing descriptions on entities.
 */
trait RestDescriptions extends RestDAO with RestContext with Descriptions {

  private def requestUrl = s"$baseUrl/description"

  override def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(
        implicit rs: BackendResource[MT], fmt: BackendWriteable[DT], rd: backend.BackendReadable[DT]): Future[DT] = {
    val url: String = enc(requestUrl, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(fmt.restFormat)).map { response =>
      val desc: DT = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(canonicalUrl(id))
      desc
    }
  }

  override def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
      implicit rs: BackendResource[MT], fmt: BackendWriteable[DT], rd: backend.BackendReadable[DT]): Future[DT] = {
    val url: String = enc(requestUrl, id, did)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .put(Json.toJson(item)(fmt.restFormat)).map { response =>
      val desc: DT = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(canonicalUrl(id))
      desc
    }
  }

  override def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(
      implicit rs: BackendResource[MT]): Future[Unit] = {
    userCall(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg): _*)
          .delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(did)
      Cache.remove(canonicalUrl(id))
    }
  }

  override def createAccessPoint[MT, DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
        implicit rs: BackendResource[MT], fmt: BackendWriteable[DT]): Future[DT] = {
    val url: String = enc(requestUrl, id, did, EntityType.AccessPoint)
    userCall(url)
        .withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(fmt.restFormat)).map { response =>
      eventHandler.handleUpdate(id)
      Cache.remove(canonicalUrl(id))
      checkErrorAndParse(response, context = Some(url))(fmt.restFormat)
    }
  }

  override def deleteAccessPoint(id: String, did: String, apid: String, logMsg: Option[String] = None): Future[Unit] = {
    val url = enc(requestUrl, id, did, EntityType.AccessPoint, apid)
    userCall(url).withHeaders(msgHeader(logMsg): _*).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
    }
  }
}