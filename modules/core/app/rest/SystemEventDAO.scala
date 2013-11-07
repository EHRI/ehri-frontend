package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.json.RestReadable
import models.base.{AnyModel, MetaModel}
import models.{SystemEvent, UserProfile}
import utils.{ListParams, SystemEventParams, PageParams}
import play.api.libs.json.Reads


/**
 * Data Access Object for Action-related requests.
 */
case class SystemEventDAO() extends RestDAO {

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/systemEvent".format(baseUrl)

  def history(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[SystemEvent]] = {
    implicit val rd: RestReadable[SystemEvent] = SystemEvent.Converter
    WS.url(enc(requestUrl, "for", id)).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Page[SystemEvent]](response)(Page.pageReads(rd.restReads))
    }
  }

  def list(params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser): Future[List[SystemEvent]] = {
    WS.url(enc(requestUrl, "list")).withQueryString((params.toSeq ++ filters.toSeq): _*)
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Reads.list[SystemEvent](SystemEvent.Converter.restReads))
    }
  }

  def subjectsFor(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[AnyModel]] = {
    WS.url(enc(requestUrl, id, "subjects")).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Page[AnyModel]](response)(Page.pageReads(AnyModel.Converter.restReads))
    }
  }
}
