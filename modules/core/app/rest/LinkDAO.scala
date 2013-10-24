package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import defines.EntityType
import models._
import play.api.libs.json.{Reads, Json}
import play.api.Logger


/**
 * Data Access Object for fetching link data.
 *
 * @param userProfile
 */
case class LinkDAO(userProfile: Option[UserProfile] = None)(implicit eventHandler: RestEventHandler) extends RestDAO {

  import EntityDAO._

  final val BODY_PARAM = "body"
  final val BODY_TYPE = "bodyType"
  final val BODY_NAME = "bodyName"

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.Link)

  implicit val linkMetaReads = Link.Converter.restReads

  /**
   * Fetch links for the given item.
   * @param id
   * @return
   */
  def getFor(id: String): Future[List[Link]] = {
    WS.url(enc(requestUrl, "for/%s?limit=1000".format(id)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Reads.list(linkMetaReads))
    }
  }

  /**
   * Create a single link.
   * @param id
   * @param src
   * @param link
   * @return
   */
  def link(id: String, src: String, link: LinkF, accessPoint: Option[String] = None): Future[Link] = {
    WS.url(enc(requestUrl, id, accessPoint.map(ap => s"${src}?${BODY_PARAM}=${ap}").getOrElse(src)))
      .withHeaders(authHeaders.toSeq: _*)
      .post(Json.toJson(link)(LinkF.Converter.restFormat)).map { response =>
      checkErrorAndParse[Link](response)(linkMetaReads)
    }
  }

  /**
   * Remove a link on an item.
   */
  def deleteLink(id: String, linkId: String): Future[Boolean] = {
    val url = enc(requestUrl, "for", id, linkId)
    Logger.logger.debug(s"DELETE LINK: $url")
    WS.url(url).withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      checkError(response)
      eventHandler.handleDelete(linkId)
      true
    }
  }


  /**
   * Remove an access point for a given item.
   * TODO: When the linking api gets sorted out, move
   * this somewhere better.
   */
  def deleteAccessPoint(id: String): Future[Boolean] = {
    val url = enc(requestUrl, "accessPoint", id)
    Logger.logger.debug(s"DELETE ACCESS POINT $url")
    WS.url(url).withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
      true
    }
  }

  /**
   * Create multiple links. NB: This function is NOT transactional.
   */
  def linkMultiple(id: String, srcToLinks: List[(String,LinkF,Option[String])]): Future[List[Link]] = {
    Future.sequence {
      srcToLinks.map {
        case (other, ann, accessPoint) => link(id, other, ann, accessPoint)
      }
    }
  }
}