package rest

import models.Entity
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile


/**
 * Data Access Object for Action-related requests.
 */
case class ActionLogDAO(val userProfile: UserProfile) extends RestDAO {

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/action".format(baseUrl)

  private val authHeaders: Map[String, String] = headers + (
    AUTH_HEADER_NAME -> userProfile.id
  )

  def history(id: String, page: Int, limit: Int): Future[Either[RestError, Page[Entity]]] = {
    implicit val entityReads = Entity.entityReads
    implicit val entityPageReads = PageReads.pageReads

    WS.url(enc(requestUrl, "for/%s?offset=%d&limit=%d".format(id, (page-1)*limit, limit)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[models.Entity]].fold(
          valid = { page =>
            Page(page.total, page.offset, page.limit, page.list)
          },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: " + e.toString)
          }
        )
      }
    }
  }
}
