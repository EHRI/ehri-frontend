package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.cache.Cache
import backend._


/**
 * Set visibility on items.
 */
trait RestVisibility extends Visibility with RestDAO {

  val eventHandler: EventHandler

  import Constants._

  private def requestUrl = s"$baseUrl/access"

  def setVisibility[MT](id: String, data: Seq[String])(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT] = {
    val url: String = enc(requestUrl, id)
    userCall(url)
        .withQueryString(data.map(a => ACCESSOR_PARAM -> a): _*)
        .post("").map { response =>
      val r = checkErrorAndParse(response, context = Some(url))(rs.restReads)
      Cache.remove(canonicalUrl(id))
      eventHandler.handleUpdate(id)
      r
    }
  }
}