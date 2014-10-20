package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json
import defines.EntityType
import backend._
import utils.{Page, PageParams}
import backend.ApiUser
import backend.Annotations


/**
 * Data Access Object for fetching annotation data.
 */
trait RestAnnotations extends Annotations with RestDAO {

  val eventHandler: EventHandler
  import Constants.ACCESSOR_PARAM

  private def requestUrl = s"$baseUrl/${EntityType.Annotation}"

  def getAnnotationsForItem[A](id: String)(implicit apiUser: ApiUser, rs: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url = enc(requestUrl, "for", id)
    val pageParams = PageParams.empty.withoutLimit
    userCall(url).withQueryString(pageParams.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rs.restReads)
    }
  }

  def createAnnotation[A,AF](id: String, ann: AF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, rs: BackendReadable[A], wr: BackendWriteable[AF], executionContext: ExecutionContext): Future[A] = {
    val url: String = enc(requestUrl, id)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .post(Json.toJson(ann)(wr.restFormat)).map { response =>
      checkErrorAndParse(response, context = Some(url))(rs.restReads)
    }
  }

  def createAnnotationForDependent[A,AF](id: String, did: String, ann: AF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, rs: BackendReadable[A], wr: BackendWriteable[AF], executionContext: ExecutionContext): Future[A] = {
    val url: String = enc(requestUrl, id, did)
    userCall(url)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .post(Json.toJson(ann)(wr.restFormat)).map { response =>
      checkErrorAndParse(response, context = Some(url))(rs.restReads)
    }
  }
}
