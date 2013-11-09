package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.json.{Reads, Json}
import defines.EntityType
import models._
import models.json.AnnotationFormat
import play.api.Logger


/**
 * Data Access Object for fetching annotation data.
 */
case class AnnotationDAO(eventHandler: RestEventHandler) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.Annotation)

  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser): Future[Map[String,List[Annotation]]] = {
    val url = enc(requestUrl, "for/%s?limit=1000".format(id))
    userCall(url).get.map { response =>
      checkErrorAndParse(response)(Reads.mapReads(Reads.list(AnnotationFormat.metaReads)))
    }
  }

  def createAnnotation(id: String, ann: AnnotationF)(implicit apiUser: ApiUser): Future[Annotation] = {
    userCall(enc(requestUrl, id)).post(Json.toJson(ann)(AnnotationFormat.restFormat)).map { response =>
      checkErrorAndParse(response)(AnnotationFormat.metaReads)
    }
  }
}