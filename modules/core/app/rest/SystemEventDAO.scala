package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import models.json.RestReadable
import models.base.AnyModel
import models.SystemEvent
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
    userCall(enc(requestUrl, "for", id)).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse[Page[SystemEvent]](response)(Page.pageReads(rd.restReads))
    }
  }

  def listEvents(params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser): Future[List[SystemEvent]] = {
    userCall(enc(requestUrl, "list")).withQueryString((params.toSeq ++ filters.toSeq): _*).get.map { response =>
      checkErrorAndParse(response)(Reads.list[SystemEvent](SystemEvent.Converter.restReads))
    }
  }

  def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[AnyModel]] = {
    userCall(enc(requestUrl, id, "subjects")).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse[Page[AnyModel]](response)(Page.pageReads(AnyModel.Converter.restReads))
    }
  }
}
