package backend.rest

import scala.concurrent.Future
import defines.EntityType
import play.api.libs.json.Json
import backend._
import utils.{Page, PageParams}
import play.api.http.Status


/**
 * Data Access Object for fetching link data.
 */
trait RestLinks extends Links with RestDAO with RestContext {

  final val BODY_PARAM = "body"
  final val BODY_TYPE = "bodyType"
  final val BODY_NAME = "bodyName"

  private def requestUrl = s"$baseUrl/${EntityType.Link}"

  /**
   * Fetch links for the given item.
   */
  override def getLinksForItem[A: Readable](id: String): Future[Page[A]] = {
    val pageParams = PageParams.empty.withoutLimit
    userCall(enc(requestUrl, "for", id)).withQueryString(pageParams.queryParams: _*)
      .get().map { response =>
      parsePage(response)(Readable[A].restReads)
    }
  }

  /**
   * Create a single link.
   */
  override def linkItems[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, src: String, link: AF, accessPoint: Option[String] = None): Future[A] = {
    val url: String = enc(requestUrl, id, src)
    userCall(url).withQueryString(accessPoint.map(a => BODY_PARAM -> a).toSeq: _*)
      .post(Json.toJson(link)(Writable[AF].restFormat)).map { response =>
      cache.remove(canonicalUrl[MT](id))
      val link: A = checkErrorAndParse[A](response, context = Some(url))(Readable[A].restReads)
      eventHandler.handleCreate(link.id)
      link
    }
  }

  /**
   * Remove a link on an item.
   */
  override def deleteLink[MT: Resource](id: String, linkId: String): Future[Boolean] = {
    val url = enc(requestUrl, "for", id, linkId)
    userCall(url).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(linkId)
      cache.remove(canonicalUrl(id))
      response.status == Status.OK
    }
  }

  /**
   * Create multiple links. NB: This function is NOT transactional.
   */
  override def linkMultiple[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, srcToLinks: Seq[(String,AF,Option[String])]): Future[Seq[A]] = {
    val done: Future[Seq[A]] = Future.sequence {
      srcToLinks.map { case (other, ann, accessPoint) =>
        linkItems(id, other, ann, accessPoint)
      }
    }
    done.map { r =>
      cache.remove(canonicalUrl(id))
      r
    }
  }
}
