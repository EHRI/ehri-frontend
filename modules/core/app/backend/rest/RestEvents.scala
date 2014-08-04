package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import models.json.RestReadable
import models.base.AnyModel
import models.{Version, SystemEvent}
import utils.{SystemEventParams, PageParams}
import backend.{Events, ApiUser, Page}
import defines.EntityType


/**
 * Data Access Object for Action-related requests.
 */
trait RestEvents extends Events with RestDAO {

  private def baseUrl = s"http://$host:$port/$mount"
  private def requestUrl = s"$baseUrl/${EntityType.SystemEvent}"

  def history(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]] = {
    implicit val rd: RestReadable[SystemEvent] = SystemEvent.Converter
    userCall(enc(requestUrl, "for", id)).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response)(rd.restReads)
    }
  }

  def versions(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Version]] = {
    implicit val rd: RestReadable[Version] = Version.Converter
    userCall(enc(requestUrl, "versions", id))
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(PageParams.streamHeader).get().map { response =>
      parsePage(response)(rd.restReads)
    }
  }

  def listEvents(params: PageParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]] = {
    userCall(enc(requestUrl, "list"))
      .withQueryString(params.queryParams ++ filters.toSeq: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(PageParams.streamHeader).get().map { response =>
      parsePage(response)(SystemEvent.Converter.restReads)
    }
  }

  def listEventsForUser(userId: String, params: PageParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]] = {
    userCall(enc(requestUrl, "forUser", userId))
      .withQueryString(params.queryParams ++ filters.toSeq: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(PageParams.streamHeader).get().map { response =>
      parsePage(response)(SystemEvent.Converter.restReads)
    }
  }

  def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[AnyModel]] = {
    userCall(enc(requestUrl, id, "subjects"))
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*).get().map { response =>
      parsePage(response)(AnyModel.Converter.restReads)
    }
  }
}


case class SystemEventDAO() extends RestEvents