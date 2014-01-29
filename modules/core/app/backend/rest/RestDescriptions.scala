package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import defines.EntityType
import play.api.Play.current
import play.api.cache.Cache
import models.json.{RestResource, RestReadable, RestConvertable}
import models.base.AnyModel
import backend.{EventHandler, ApiUser}


/**
 * Data Access Object for managing descriptions on entities.
 */
trait RestDescriptions extends RestDAO {

  private val entities = new EntityDAO(eventHandler)

  val eventHandler: EventHandler

  private def requestUrl = "http://%s:%d/%s/description".format(host, port, mount)

  def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    userCall(enc(requestUrl, id)).withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response)
      entities.get(id).map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        item
      }
    }
  }

  def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    userCall(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg): _*)
        .put(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      checkError(response)
      entities.get(id).map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        item
      }
    }
  }

  def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[Boolean] = {
    userCall(enc(requestUrl, id, did)).withHeaders(msgHeader(logMsg): _*)
          .delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(did)
      Cache.remove(id)
      true
    }
  }

  def createAccessPoint[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[(MT,DT)] = {
    userCall(enc(requestUrl, id, did, EntityType.AccessPoint.toString))
        .withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(fmt.restFormat)).flatMap { response =>
      entities.get(id).map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        (item, checkErrorAndParse[DT](response)(fmt.restFormat))
      }
    }
  }

  def deleteAccessPoint[MT <: AnyModel](id: String, did: String, apid: String, logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    userCall(enc(requestUrl, id, did, EntityType.AccessPoint.toString, apid)).withHeaders(msgHeader(logMsg): _*).delete().flatMap { response =>
      entities.get(id).map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        item
      }
    }
  }

  def deleteAccessPoint(id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = enc(requestUrl, "accessPoint", id)
    userCall(url).withHeaders(msgHeader(logMsg): _*).delete.map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
      true
    }
  }
}


case class DescriptionDAO(eventHandler: EventHandler) extends RestDescriptions