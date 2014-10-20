package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json
import defines.EntityType
import models._
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

  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser, rs: BackendReadable[Annotation], executionContext: ExecutionContext): Future[Page[Annotation]] = {
    val url = enc(requestUrl, "for", id)
    val pageParams = PageParams.empty.withoutLimit
    userCall(url).withQueryString(pageParams.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rs.restReads)
    }
  }

  def createAnnotation(id: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, rs: BackendReadable[Annotation], wr: BackendWriteable[AnnotationF], executionContext: ExecutionContext): Future[Annotation] = {
    val url: String = enc(requestUrl, id)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .post(Json.toJson(ann)(wr.restFormat)).map { response =>
      checkErrorAndParse(response, context = Some(url))(rs.restReads)
    }
  }

  def createAnnotationForDependent(id: String, did: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, rs: BackendReadable[Annotation], wr: BackendWriteable[AnnotationF], executionContext: ExecutionContext): Future[Annotation] = {
    val url: String = enc(requestUrl, id, did)
    userCall(url)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .post(Json.toJson(ann)(wr.restFormat)).map { response =>
      checkErrorAndParse(response, context = Some(url))(rs.restReads)
    }
  }
}