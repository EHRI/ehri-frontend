package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import play.api.libs.json.{Json, Writes, JsArray, JsValue}
import defines.{EntityType,ContentType}
import models.Entity
import models.UserProfile
import models.base.Persistable
import play.api.http.Status
import play.api.Play.current
import play.api.cache.Cache
import models.json.RestConvertable


/**
 * Data Access Object for managing descriptions on entities.
 *
 * @param userProfile
 */
case class DescriptionDAO(entityType: EntityType.Type, userProfile: Option[UserProfile] = None) extends RestDAO {

  implicit val entityReads = Entity.entityReads
  implicit val entityPageReads = PageReads.pageReads
  import EntityDAO._

  def requestUrl = "http://%s:%d/%s/description".format(host, port, mount)

  def createDescription[DT](id: String, item: DT,
      logMsg: Option[String] = None)(implicit fmt: RestConvertable[DT]): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id))
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .post(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response) match {
        case Left(err) => Future.successful(Left(err))
        case Right(r) => {
          EntityDAO(entityType, userProfile).get(id).map {
            case Right(updated) => {
              EntityDAO.handleUpdate(updated)
              Cache.remove(id)
              Right(jsonToEntity(r.json))
            }
            case err => err
          }
        }
      }
    }
  }

  def updateDescription[DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
      implicit fmt: RestConvertable[DT]): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .put(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response) match {
        case Left(err) => Future.successful(Left(err))
        case Right(r) => {
          EntityDAO(entityType, userProfile).get(id).map {
            case Right(updated) => {
              EntityDAO.handleUpdate(updated)
              Cache.remove(id)
              Right(jsonToEntity(r.json))
            }
            case err => err
          }
        }
      }
    }
  }

  def deleteDescription(id: String, did: String, logMsg: Option[String] = None): Future[Either[RestError, Boolean]] = {
    WS.url(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .delete.flatMap { response =>
      EntityDAO(entityType, userProfile).get(id).map {
        case Right(updated) => {
          EntityDAO.handleUpdate(updated)
          Cache.remove(id)
          Right(true)
        }
        case Left(err) => Left(err)
      }
    }
  }

  // FIXME: Move these elsewhere...
  def createAccessPoint[DT](id: String, did: String, item: DT,
                        logMsg: Option[String] = None)(implicit ow: Writes[DT]): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id, did, EntityType.AccessPoint.toString))
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .post(Json.toJson(item)).flatMap { response =>
      checkError(response) match {
        case Left(err) => Future.successful(Left(err))
        case Right(r) => {
          EntityDAO(entityType, userProfile).get(id).map {
            case Right(updated) => {
              EntityDAO.handleUpdate(updated)
              Cache.remove(id)
              Right(jsonToEntity(r.json))
            }
            case err => err
          }
        }
      }
    }
  }

  def deleteAccessPoint(id: String, did: String, apid: String, logMsg: Option[String] = None): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id, did, apid)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .delete.flatMap { response =>
        EntityDAO(entityType, userProfile).get(id).map {
          case Right(updated) => {
            EntityDAO.handleUpdate(updated)
            Cache.remove(id)
            Right(updated)
          }
          case err => err
        }
    }
  }
}
