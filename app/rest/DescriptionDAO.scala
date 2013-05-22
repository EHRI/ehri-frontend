package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import play.api.libs.json.{ JsArray, JsValue }
import defines.{EntityType,ContentType}
import models.Entity
import models.UserProfile
import models.base.Persistable
import play.api.http.Status
import play.api.Play.current
import play.api.cache.Cache


/**
 * Data Access Object for managing descriptions on entities.
 *
 * @param userProfile
 */
case class DescriptionDAO(userProfile: Option[UserProfile] = None) extends RestDAO {

  implicit val entityReads = Entity.entityReads
  implicit val entityPageReads = PageReads.pageReads
  import EntityDAO._

  def requestUrl = "http://%s:%d/%s/description".format(host, port, mount)

  def createDescription(id: String, item: Persistable,
      logMsg: Option[String] = None): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id))
      .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .post(item.toJson).map { response =>
      checkError(response).right.map { r =>
        val entity = jsonToEntity(r.json)
        EntityDAO.handleUpdate(entity)
        Cache.remove(id)
        entity
      }
    }
  }

  def updateDescription(id: String, did: String, item: Persistable, logMsg: Option[String] = None): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .put(item.toJson).map { response =>
      checkError(response).right.map { r =>
        val entity = jsonToEntity(r.json)
        EntityDAO.handleUpdate(entity)
        Cache.remove(id)
        entity
      }
    }
  }

  def deleteDescription(id: String, did: String, logMsg: Option[String] = None): Future[Either[RestError, Boolean]] = {
    WS.url(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .delete.map { response =>
      checkError(response).right.map(r => {
        // FIXME: This is unfortunate. Since descriptions are indexed as individual
        // items, deleting one means deleting it individually by ID
        EntityDAO.handleDelete(did)
        Cache.remove(id)
        r.status == Status.OK
      })
    }
  }

  // FIXME: Move these elsewhere...
  def createAccessPoint(id: String, did: String, item: Persistable,
                        logMsg: Option[String] = None): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id, did, EntityType.AccessPoint.toString))
      .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .post(item.toJson).map { response =>
      checkError(response).right.map { r =>
        Cache.remove(id)
        EntityDAO.handleUpdate(jsonToEntity(r.json))
      }
    }
  }

  def deleteAccessPoint(id: String, did: String, apid: String, logMsg: Option[String] = None): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id, did, apid)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .delete.map { response =>
      checkError(response).right.map(r => {
        // FIXME: This is unfortunate. Since descriptions are indexed as individual
        // items, deleting one means deleting it individually by ID
        EntityDAO.handleDelete(did)
        Cache.remove(id)
        jsonToEntity(r.json)
      })
    }
  }
}
