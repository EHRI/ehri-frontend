package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import play.api.libs.json._
import defines.{EntityType,ContentTypes}
import models.{UserProfile, Entity}
import play.api.Play.current
import play.api.cache.Cache
import models.json.{RestReadable, RestConvertable}
import models.base.AnyModel


/**
 * Data Access Object for managing descriptions on entities.
 */
case class DescriptionDAO(entityType: EntityType.Type)(implicit eventHandler: RestEventHandler) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/description".format(host, port, mount)

  def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[MT] = {
    WS.url(enc(requestUrl, id))
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .post(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response)
      EntityDAO(entityType).getJson(id).map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        item.as[MT](rd.restReads)
      }
    }
  }

  def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[MT] = {
    WS.url(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .put(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response)
      EntityDAO(entityType).getJson(id).map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        item.as[MT](rd.restReads)
      }
    }
  }

  def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[Boolean] = {
    WS.url(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
          .delete.map { response =>
      eventHandler.handleDelete(did)
      Cache.remove(id)
      true
    }
  }

  // FIXME: Move these elsewhere...
  def createAccessPoint[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[(MT,DT)] = {
    WS.url(enc(requestUrl, id, did, EntityType.AccessPoint.toString))
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .post(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      EntityDAO(entityType).getJson(id).map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        (item.as[MT](rd.restReads), checkError(response).json.as[DT](fmt.restFormat))
      }
    }
  }

  def deleteAccessPoint[MT <: AnyModel](id: String, did: String, apid: String, logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT] = {
    WS.url(enc(requestUrl, id, did, apid)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .delete.flatMap { response =>
      EntityDAO(entityType).getJson(id).map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        item.as[MT](rd.restReads)
      }
    }
  }
}
