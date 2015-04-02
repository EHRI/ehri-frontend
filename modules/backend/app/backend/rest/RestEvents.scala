package backend.rest

import scala.concurrent.Future
import utils._
import backend.{Readable, Events}
import defines.EntityType


/**
 * Data Access Object for Action-related requests.
 */
trait RestEvents extends Events with RestDAO with RestContext {

  private def requestUrl = s"$baseUrl/${EntityType.SystemEvent}"

  override def history[A: Readable](id: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[A]] = {
    val url: String = enc(requestUrl, "for", id)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Readable[A].restReads)
  }

  override def versions[A: Readable](id: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(requestUrl, "versions", id)
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }

  override def listEvents[A: Readable](params: RangeParams, filters: SystemEventParams): Future[RangePage[A]] = {
    val url = enc(requestUrl, "list")
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Readable[A].restReads)
  }

  override def listEventsByUser[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams): Future[RangePage[A]] = {
    val url: String = enc(requestUrl, "byUser", userId)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Readable[A].restReads)
  }

  override def listEventsForUser[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams): Future[RangePage[A]] = {
    val url: String = enc(requestUrl, "forUser", userId)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Readable[A].restReads)
  }

  override def subjectsForEvent[A: Readable](id: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(requestUrl, id, "subjects")
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }
}
