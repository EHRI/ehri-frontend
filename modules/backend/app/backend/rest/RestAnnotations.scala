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
  implicit def apiUser: ApiUser
  implicit def executionContext: ExecutionContext

  import Constants.ACCESSOR_PARAM

  private def requestUrl = s"$baseUrl/${EntityType.Annotation}"

  override def getAnnotationsForItem[A](id: String)(implicit rs: BackendReadable[A]): Future[Page[A]] = {
    val url = enc(requestUrl, "for", id)
    val pageParams = PageParams.empty.withoutLimit
    userCall(url).withQueryString(pageParams.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rs.restReads)
    }
  }

  override def createAnnotation[A <: WithId, AF](id: String, ann: AF, accessors: Seq[String] = Nil)(implicit rs: BackendReadable[A], wr: BackendWriteable[AF]): Future[A] = {
    val url: String = enc(requestUrl, id)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .post(Json.toJson(ann)(wr.restFormat)).map { response =>
      val annotation: A = checkErrorAndParse[A](response, context = Some(url))(rs.restReads)
      eventHandler.handleCreate(annotation.id)
      annotation
    }
  }

  override def createAnnotationForDependent[A <: WithId, AF](id: String, did: String, ann: AF, accessors: Seq[String] = Nil)(implicit rs: BackendReadable[A], wr: BackendWriteable[AF]): Future[A] = {
    val url: String = enc(requestUrl, id, did)
    userCall(url)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .post(Json.toJson(ann)(wr.restFormat)).map { response =>
      val annotation: A = checkErrorAndParse[A](response, context = Some(url))(rs.restReads)
      eventHandler.handleCreate(annotation.id)
      annotation
    }
  }
}
