package backend.rest

import scala.concurrent.Future
import backend._


/**
 * Set visibility on items.
 */
trait RestVisibility extends Visibility with RestDAO with RestContext {

  import Constants._

  private def requestUrl = s"$baseUrl/access"

  override def setVisibility[MT: Resource](id: String, data: Seq[String]): Future[MT] = {
    val url: String = enc(requestUrl, id)
    userCall(url)
        .withQueryString(data.map(a => ACCESSOR_PARAM -> a): _*)
        .post("").map { response =>
      val r = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      cache.remove(canonicalUrl(id))
      eventHandler.handleUpdate(id)
      r
    }
  }
}