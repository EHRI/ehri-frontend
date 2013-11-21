package backend.rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.json.{Reads, Json}
import defines.EntityType
import models._
import models.json.AnnotationFormat
import backend.{Annotations, EventHandler, ApiUser}


/**
 * Data Access Object for fetching annotation data.
 */
trait RestAnnotations extends Annotations with RestDAO {

  val eventHandler: EventHandler
  import Constants.{ACCESSOR_PARAM,LIMIT_PARAM}

  private def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.Annotation)

  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser): Future[Map[String,List[Annotation]]] = {
    val url = enc(requestUrl, "for", id)
    userCall(url).withQueryString(LIMIT_PARAM -> "-1").get().map { response =>
      checkErrorAndParse(response)(Reads.mapReads(Reads.list(AnnotationFormat.metaReads)))
    }
  }

  def createAnnotation(id: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser): Future[Annotation] = {
    userCall(enc(requestUrl, id))
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .post(Json.toJson(ann)(AnnotationFormat.restFormat)).map { response =>
      checkErrorAndParse(response)(AnnotationFormat.metaReads)
    }
  }

  def createAnnotationForDependent(id: String, did: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser): Future[Annotation] = {
    userCall(enc(requestUrl, id, did))
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .post(Json.toJson(ann)(AnnotationFormat.restFormat)).map { response =>
      checkErrorAndParse(response)(AnnotationFormat.metaReads)
    }
  }
}

case class AnnotationDAO(eventHandler: EventHandler) extends RestAnnotations