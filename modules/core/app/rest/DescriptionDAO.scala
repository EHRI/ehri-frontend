package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import play.api.libs.json._
import defines.{EntityType,ContentType}
import models.{UserProfile, Entity}
import play.api.Play.current
import play.api.cache.Cache
import models.json.{RestReadable, RestConvertable}


/**
 * Data Access Object for managing descriptions on entities.
 *
 * @param userProfile
 */
case class DescriptionDAO[MT](entityType: EntityType.Type, userProfile: Option[UserProfile] = None) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/description".format(host, port, mount)

  def createDescription[DT](id: String, item: DT, logMsg: Option[String] = None)(
        implicit fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    WS.url(enc(requestUrl, id))
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .post(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response) match {
        case Left(err) => Future.successful(Left(err))
        case Right(r) => {
          EntityDAO[MT](entityType, userProfile).getJson(id).map {
            case Right(item) => {
              EntityDAO.handleUpdate(item)
              Cache.remove(id)
              Right(item.as[MT](rd.restReads))
            }
            case Left(err) => Left(err)
          }
        }
      }
    }
  }

  def updateDescription[DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
      implicit fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    WS.url(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .put(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response) match {
        case Left(err) => Future.successful(Left(err))
        case Right(r) => {
          EntityDAO[MT](entityType, userProfile).getJson(id).map {
            case Right(item) => {
              EntityDAO.handleUpdate(item)
              println("HANDLING UPDATE: " + item)
              Cache.remove(id)
              Right(item.as[MT](rd.restReads))
            }
            case Left(err) => Left(err)
          }
        }
      }
    }
  }

  def deleteDescription(id: String, did: String, logMsg: Option[String] = None)(
        implicit rd: RestReadable[MT]): Future[Either[RestError, Boolean]] = {
    WS.url(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .delete.flatMap { response =>
      EntityDAO[MT](entityType, userProfile).getJson(id).map {
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
  def createAccessPoint[DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
        implicit fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, (MT,DT)]] = {
    WS.url(enc(requestUrl, id, did, EntityType.AccessPoint.toString))
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .post(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response) match {
        case Left(err) => Future.successful(Left(err))
        case Right(r) => {
          EntityDAO[MT](entityType, userProfile).getJson(id).map {
            case Right(item) => {
              EntityDAO.handleUpdate(item)
              Cache.remove(id)
              Right((item.as[MT](rd.restReads), r.json.as[DT](fmt.restFormat)))
            }
            case Left(err) => Left(err)
          }
        }
      }
    }
  }

  def deleteAccessPoint[MT](id: String, did: String, apid: String, logMsg: Option[String] = None)(
        implicit rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    WS.url(enc(requestUrl, id, did, apid)).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .delete.flatMap { response =>
        EntityDAO[MT](entityType, userProfile).getJson(id).map {
          case Right(item) => {
            EntityDAO.handleUpdate(item)
            Cache.remove(id)
            Right(item.as[MT](rd.restReads))
          }
          case Left(err) => Left(err)
        }
    }
  }
}
