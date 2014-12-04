package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import utils._
import backend.{BackendReadable, Events, ApiUser}
import defines.EntityType


/**
 * Data Access Object for Action-related requests.
 */
trait RestEvents extends Events with RestDAO {

  private def requestUrl = s"$baseUrl/${EntityType.SystemEvent}"

  def history[A](id: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, "for", id)
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def versions[A](id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, "versions", id)
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def listEvents[A](params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url = enc(requestUrl, "list")
    userCall(url)
      .withQueryString(params.queryParams ++ filters.toSeq: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def listEventsByUser[A](userId: String, params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, "byUser", userId)
    userCall(url)
      .withQueryString(params.queryParams ++ filters.toSeq: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def listEventsForUser[A](userId: String, params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, "forUser", userId)
    userCall(url)
      .withQueryString(params.queryParams ++ filters.toSeq: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def subjectsForEvent[A](id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, id, "subjects")
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }
}
