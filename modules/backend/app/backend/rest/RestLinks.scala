package backend.rest

import play.api.cache.Cache

import scala.concurrent.{ExecutionContext, Future}
import defines.EntityType
import play.api.libs.json.Json
import backend._
import utils.{Page, PageParams}
import backend.ApiUser
import play.api.http.Status


/**
 * Data Access Object for fetching link data.
 */
trait RestLinks extends Links with RestDAO {

  val eventHandler: EventHandler
  implicit def apiUser: ApiUser

  final val BODY_PARAM = "body"
  final val BODY_TYPE = "bodyType"
  final val BODY_NAME = "bodyName"

  private def requestUrl = s"$baseUrl/${EntityType.Link}"

  /**
   * Fetch links for the given item.
   */
  override def getLinksForItem[A](id: String)(implicit rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val pageParams = PageParams.empty.withoutLimit
    userCall(enc(requestUrl, "for", id)).withQueryString(pageParams.queryParams: _*)
      .get().map { response =>
      parsePage(response)(rd.restReads)
    }
  }

  /**
   * Create a single link.
   */
  override def linkItems[MT, A <: WithId, AF](id: String, src: String, link: AF, accessPoint: Option[String] = None)(implicit rs: BackendResource[MT], rd: BackendReadable[A], wd: BackendWriteable[AF],  executionContext: ExecutionContext): Future[A] = {
    val url: String = enc(requestUrl, id, src)
    userCall(url).withQueryString(accessPoint.map(a => BODY_PARAM -> a).toSeq: _*)
      .post(Json.toJson(link)(wd.restFormat)).map { response =>
      Cache.remove(canonicalUrl(id))
      val link: A = checkErrorAndParse[A](response, context = Some(url))(rd.restReads)
      eventHandler.handleCreate(link.id)
      link
    }
  }

  /**
   * Remove a link on an item.
   */
  override def deleteLink[MT](id: String, linkId: String)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[Boolean] = {
    val url = enc(requestUrl, "for", id, linkId)
    userCall(url).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(linkId)
      Cache.remove(canonicalUrl(id))
      response.status == Status.OK
    }
  }

  /**
   * Create multiple links. NB: This function is NOT transactional.
   */
  override def linkMultiple[MT, A <: WithId, AF](id: String, srcToLinks: Seq[(String,AF,Option[String])])(implicit rs: BackendResource[MT], rd: BackendReadable[A], wd: BackendWriteable[AF], executionContext: ExecutionContext): Future[Seq[A]] = {
    val done: Future[Seq[A]] = Future.sequence {
      srcToLinks.map { case (other, ann, accessPoint) =>
        linkItems(id, other, ann, accessPoint)
      }
    }
    done.map { r =>
      Cache.remove(canonicalUrl(id))
      r
    }
  }
}
