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

  def eventHandler: EventHandler
  implicit def apiUser: ApiUser
  implicit def executionContext: ExecutionContext

  import backend.rest.Constants.ACCESSOR_PARAM

  private def requestUrl = enc(baseUrl, EntityType.Annotation)

  override def getAnnotationsForItem[A: Readable](id: String): Future[Page[A]] = {
    val url = enc(requestUrl, "for", id)
    val pageParams = PageParams.empty.withoutLimit
    userCall(url).withQueryString(pageParams.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }

  override def createAnnotation[A <: WithId : Readable, AF: Writable](id: String, ann: AF, accessors: Seq[String] = Nil): Future[A] = {
    val url: String = enc(requestUrl, id)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .post(Json.toJson(ann)(Writable[AF].restFormat)).map { response =>
      val annotation: A = checkErrorAndParse[A](response, context = Some(url))(Readable[A].restReads)
      eventHandler.handleCreate(annotation.id)
      annotation
    }
  }

  override def createAnnotationForDependent[A <: WithId : Readable, AF: Writable](id: String, did: String, ann: AF, accessors: Seq[String] = Nil): Future[A] = {
    val url: String = enc(requestUrl, id, did)
    userCall(url)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .post(Json.toJson(ann)(Writable[AF].restFormat)).map { response =>
      val annotation: A = checkErrorAndParse[A](response, context = Some(url))(Readable[A].restReads)
      eventHandler.handleCreate(annotation.id)
      annotation
    }
  }
}
