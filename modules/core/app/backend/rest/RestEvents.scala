package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import models.base.AnyModel
import models.{Version, SystemEvent}
import utils.{Page, SystemEventParams, PageParams}
import backend.{BackendReadable, Events, ApiUser}
import defines.EntityType


/**
 * Data Access Object for Action-related requests.
 */
trait RestEvents extends Events with RestDAO {

  private def baseUrl = s"http://$host:$port/$mount"
  private def requestUrl = s"$baseUrl/${EntityType.SystemEvent}"

  def history(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]] = {
    implicit val rd: BackendReadable[SystemEvent] = SystemEvent.Converter
    val url: String = enc(requestUrl, "for", id)
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def versions(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Version]] = {
    implicit val rd: BackendReadable[Version] = Version.Converter
    val url: String = enc(requestUrl, "versions", id)
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(PageParams.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def listEvents(params: PageParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]] = {
    val url = enc(requestUrl, "list")
    userCall(url)
      .withQueryString(params.queryParams ++ filters.toSeq: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(PageParams.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(SystemEvent.Converter.restReads)
    }
  }

  def listEventsByUser(userId: String, params: PageParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]] = {
    val url: String = enc(requestUrl, "byUser", userId)
    userCall(url)
      .withQueryString(params.queryParams ++ filters.toSeq: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(PageParams.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(SystemEvent.Converter.restReads)
    }
  }

  def listEventsForUser(userId: String, params: PageParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]] = {
    val url: String = enc(requestUrl, "forUser", userId)
    userCall(url)
      .withQueryString(params.queryParams ++ filters.toSeq: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(PageParams.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(SystemEvent.Converter.restReads)
    }
  }

  def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[AnyModel]] = {
    val url: String = enc(requestUrl, id, "subjects")
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*).get().map { response =>
      parsePage(response, context = Some(url))(AnyModel.Converter.restReads)
    }
  }
}


case class SystemEventDAO() extends RestEvents
