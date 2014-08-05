package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Reads, Json}
import defines.EntityType
import models._
import backend.{Annotations, EventHandler, ApiUser}
import utils.{Page, PageParams}


/**
 * Data Access Object for fetching annotation data.
 */
trait RestAnnotations extends Annotations with RestDAO {

  val eventHandler: EventHandler
  import Constants.ACCESSOR_PARAM

  private def requestUrl = s"http://$host:$port/$mount/${EntityType.Annotation}"

  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Annotation]] = {
    val url = enc(requestUrl, "for", id)
    val pageParams = PageParams.empty.withoutLimit
    userCall(url).withQueryString(pageParams.queryParams: _*).get().map { response =>
      parsePage(response)(Annotation.Converter.restReads)
    }
  }

  def createAnnotation(id: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Annotation] = {
    userCall(enc(requestUrl, id))
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .post(Json.toJson(ann)(AnnotationF.annotationFormat)).map { response =>
      checkErrorAndParse(response)(Annotation.Converter.restReads)
    }
  }

  def createAnnotationForDependent(id: String, did: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Annotation] = {
    userCall(enc(requestUrl, id, did))
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .post(Json.toJson(ann)(AnnotationF.annotationFormat)).map { response =>
      checkErrorAndParse(response)(Annotation.Converter.restReads)
    }
  }
}

case class AnnotationDAO(eventHandler: EventHandler) extends RestAnnotations