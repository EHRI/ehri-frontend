package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import defines.EntityType
import models._
import play.api.libs.json.{Reads, Json}
import play.api.Logger


/**
 * Data Access Object for fetching link data.
 */
case class LinkDAO(eventHandler: RestEventHandler) extends RestDAO {

  final val BODY_PARAM = "body"
  final val BODY_TYPE = "bodyType"
  final val BODY_NAME = "bodyName"

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.Link)

  implicit val linkMetaReads = Link.Converter.restReads

  /**
   * Fetch links for the given item.
   */
  def getLinksForItem(id: String)(implicit apiUser: ApiUser): Future[List[Link]] = {
    userCall(enc(requestUrl, "for/%s?limit=1000".format(id))).get.map { response =>
      checkErrorAndParse(response)(Reads.list(linkMetaReads))
    }
  }

  /**
   * Create a single link.
   */
  def linkItems(id: String, src: String, link: LinkF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser): Future[Link] = {
    userCall(enc(requestUrl, id, accessPoint.map(ap => s"${src}?${BODY_PARAM}=${ap}").getOrElse(src)))
      .post(Json.toJson(link)(LinkF.Converter.restFormat)).map { response =>
      checkErrorAndParse[Link](response)(linkMetaReads)
    }
  }

  /**
   * Remove a link on an item.
   */
  def deleteLink(id: String, linkId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    val url = enc(requestUrl, "for", id, linkId)
    userCall(url).delete.map { response =>
      checkError(response)
      eventHandler.handleDelete(linkId)
      true
    }
  }

  /**
   * Create multiple links. NB: This function is NOT transactional.
   */
  def linkMultiple(id: String, srcToLinks: List[(String,LinkF,Option[String])])(implicit apiUser: ApiUser): Future[List[Link]] = {
    Future.sequence {
      srcToLinks.map {
        case (other, ann, accessPoint) => linkItems(id, other, ann, accessPoint)
      }
    }
  }
}