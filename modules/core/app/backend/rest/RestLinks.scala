package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import defines.EntityType
import models._
import play.api.libs.json.{Reads, Json}
import backend.{Links, EventHandler, ApiUser}
import utils.{Page, PageParams}


/**
 * Data Access Object for fetching link data.
 */
trait RestLinks extends Links with RestDAO {

  val eventHandler: EventHandler

  final val BODY_PARAM = "body"
  final val BODY_TYPE = "bodyType"
  final val BODY_NAME = "bodyName"

  private def requestUrl = s"http://$host:$port/$mount/${EntityType.Link}"

  implicit val linkMetaReads = Link.Converter.restReads

  /**
   * Fetch links for the given item.
   */
  def getLinksForItem(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Link]] = {
    val pageParams = PageParams.empty.withoutLimit
    userCall(enc(requestUrl, "for", id)).withQueryString(pageParams.queryParams: _*)
      .get().map { response =>
      parsePage(response)(linkMetaReads)
    }
  }

  /**
   * Create a single link.
   */
  def linkItems(id: String, src: String, link: LinkF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Link] = {
    val url: String = enc(requestUrl, id, src)
    userCall(url).withQueryString(accessPoint.map(a => BODY_PARAM -> a).toSeq: _*)
      .post(Json.toJson(link)(LinkF.Converter.restFormat)).map { response =>
      checkErrorAndParse[Link](response, context = Some(url))(linkMetaReads)
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
  def linkMultiple(id: String, srcToLinks: Seq[(String,LinkF,Option[String])])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Seq[Link]] = {
    Future.sequence {
      srcToLinks.map {
        case (other, ann, accessPoint) => linkItems(id, other, ann, accessPoint)
      }
    }
  }
}

case class LinkDAO(eventHandler: EventHandler) extends RestLinks