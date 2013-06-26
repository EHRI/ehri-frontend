package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.json.RestReadable
import models.base.MetaModel
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
        r.json.validate[Page[SystemEventMeta]](PageReads.pageReads(rd.restReads)).fold(
          valid = { page => page },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: " + e.toString)
          }
        )
      }
    }
  }

  def subjectsFor(id: String, params: RestPageParams): Future[Either[RestError, Page[MetaModel[_]]]] = {
    WS.url(enc(requestUrl, id, "subjects") + params.toString)
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[MetaModel[_]]](PageReads.pageReads(MetaModel.Converter.restReads)).fold(
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
