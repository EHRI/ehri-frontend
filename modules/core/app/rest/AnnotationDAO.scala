package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.json.{Reads, Json}
import defines.EntityType
import models._
import models.json.{AnnotationFormat, RestReadable, RestConvertable}


/**
 * Data Access Object for fetching annotation data.
 *
 * @param userProfile
 */
case class AnnotationDAO(userProfile: Option[UserProfile] = None) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.Annotation)

  def getFor(id: String): Future[Either[RestError, Map[String,List[Annotation]]]] = {
    WS.url(enc(requestUrl, "for/%s?limit=1000".format(id)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Reads.mapReads(Reads.list(AnnotationFormat.metaReads)))
    }
  }

  def create(id: String, ann: AnnotationF): Future[Either[RestError, Annotation]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*)
      .post(Json.toJson(ann)(AnnotationFormat.restFormat)).map { response =>
      checkErrorAndParse(response)(AnnotationFormat.metaReads)
    }
  }
}