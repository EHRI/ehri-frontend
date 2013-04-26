package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import defines.EntityType
import models._


/**
 * Data Access Object for fetching link data.
 *
 * @param userProfile
 */
case class LinkDAO(userProfile: Option[UserProfile] = None) extends RestDAO {

  implicit val entityReads = Entity.entityReads
  implicit val entityPageReads = PageReads.pageReads
  import EntityDAO._

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.Link)

  def getFor(id: String): Future[Either[RestError, List[Link]]] = {

    WS.url(enc(requestUrl, "for/%s?limit=1000".format(id)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[List[models.Entity]].fold(
          valid = {
            lst => lst.map(l => Link(l))
          },
          invalid = { e =>
            println(r.json)
            sys.error("Unable to decode list result: " + e.toString)
          }
        )
      }
    }
  }

  def link(id: String, src: String, ann: LinkF): Future[Either[RestError, Link]] = {
    WS.url(enc(requestUrl, id, src)).withHeaders(authHeaders.toSeq: _*)
      .post(ann.toJson).map { response =>
      checkError(response).right.map(r => Link(jsonToEntity(r.json)))
    }
  }
}