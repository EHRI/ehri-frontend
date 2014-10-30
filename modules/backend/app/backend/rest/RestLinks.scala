package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import defines.EntityType
import play.api.libs.json.Json
import backend._
import utils.{Page, PageParams}
import backend.ApiUser


/**
 * Data Access Object for fetching link data.
 */
trait RestLinks extends Links with RestDAO {

  val eventHandler: EventHandler

  final val BODY_PARAM = "body"
  final val BODY_TYPE = "bodyType"
  final val BODY_NAME = "bodyName"

  private def requestUrl = s"$baseUrl/${EntityType.Link}"

  /**
   * Fetch links for the given item.
   */
  def getLinksForItem[A](id: String)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val pageParams = PageParams.empty.withoutLimit
    userCall(enc(requestUrl, "for", id)).withQueryString(pageParams.queryParams: _*)
      .get().map { response =>
      parsePage(response)(rd.restReads)
    }
  }

  /**
   * Create a single link.
   */
  def linkItems[A,AF](id: String, src: String, link: AF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser, rd: BackendReadable[A], wd: BackendWriteable[AF],  executionContext: ExecutionContext): Future[A] = {
    val url: String = enc(requestUrl, id, src)
    userCall(url).withQueryString(accessPoint.map(a => BODY_PARAM -> a).toSeq: _*)
      .post(Json.toJson(link)(wd.restFormat)).map { response =>
      checkErrorAndParse(response, context = Some(url))(rd.restReads)
    }
  }

  /**
   * Remove a link on an item.
   */
  def deleteLink(id: String, linkId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = enc(requestUrl, "for", id, linkId)
    userCall(url).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(linkId)
      true
    }
  }

  /**
   * Create multiple links. NB: This function is NOT transactional.
   */
  def linkMultiple[A,AF](id: String, srcToLinks: Seq[(String,AF,Option[String])])(implicit apiUser: ApiUser, rd: BackendReadable[A], wd: BackendWriteable[AF], executionContext: ExecutionContext): Future[Seq[A]] = Future.sequence {
    srcToLinks.map {
      case (other, ann, accessPoint) => linkItems(id, other, ann, accessPoint)
    }
  }
}
