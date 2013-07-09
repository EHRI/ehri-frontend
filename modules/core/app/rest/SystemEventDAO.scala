package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.json.RestReadable
import models.base.{AnyModel, MetaModel}
import models.{SystemEventMeta, UserProfileMeta}


/**
 * Data Access Object for Action-related requests.
 */
case class SystemEventDAO(userProfile: Option[UserProfileMeta]) extends RestDAO {

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/systemEvent".format(baseUrl)

  def history(id: String, params: RestPageParams): Future[Either[RestError, Page[SystemEventMeta]]] = {
    implicit val rd: RestReadable[SystemEventMeta] = SystemEventMeta.Converter
    WS.url(enc(requestUrl, "for", id) + params.toString)
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[SystemEventMeta]](Page.pageReads(rd.restReads)).fold(
          valid = { page => page },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: " + e.toString)
          }
        )
      }
    }
  }

  def subjectsFor(id: String, params: RestPageParams): Future[Either[RestError, Page[AnyModel]]] = {
    WS.url(enc(requestUrl, id, "subjects") + params.toString)
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[AnyModel]](Page.pageReads(AnyModel.Converter.restReads)).fold(
          valid = { page =>
            page
          },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: " + e.toString)
          }
        )
      }
    }
  }
}
