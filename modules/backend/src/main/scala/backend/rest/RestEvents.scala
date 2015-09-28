package backend.rest

import play.api.libs.json.Reads

import scala.concurrent.Future
import utils._
import backend.{Readable, Events}
import defines.EntityType


/**
 * Data Access Object for Action-related requests.
 */
trait RestEvents extends Events with RestDAO with RestContext {

  private def requestUrl = s"$baseUrl/${EntityType.SystemEvent}"

  override def history[A: Readable](id: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(requestUrl, "aggregateFor", id)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Reads.seq(Readable[A].restReads))
  }

  override def versions[A: Readable](id: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(baseUrl, EntityType.Version, "for",  id)
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }

  override def listEvents[A: Readable](params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    fetchRange(userCall(requestUrl, filters.toSeq), params, Some(requestUrl))(Reads.seq(Readable[A].restReads))
  }

  override def listUserActions[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(requestUrl, "aggregateByUser", userId)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Reads.seq(Readable[A].restReads))
  }

  override def listEventsForUser[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(requestUrl, "aggregateForUser", userId)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Reads.seq(Readable[A].restReads))
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
