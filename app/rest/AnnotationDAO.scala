package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.json.{Reads, Json}
import defines.EntityType
import models._
import models.json.{RestReadable, RestConvertable}


/**
 * Data Access Object for fetching annotation data.
 *
 * @param userProfile
 */
case class AnnotationDAO(userProfile: Option[UserProfileMeta] = None) extends RestDAO {

  implicit val annotationReads = models.json.AnnotationFormat.metaReads

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.Annotation)

  def getFor(id: String): Future[Either[RestError, Map[String,List[AnnotationMeta]]]] = {
    WS.url(enc(requestUrl, "for/%s?limit=1000".format(id)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Map[String, List[AnnotationMeta]]](Reads.mapReads(Reads.list(annotationReads))).fold(
          valid = { map => map },
          invalid = { e =>
            println(r.json)
            sys.error("Unable to decode list result: " + e.toString)
          }
        )
      }
    }
  }

  def create(id: String, ann: AnnotationF)(implicit fmt: RestConvertable[AnnotationF], rd: RestReadable[AnnotationMeta]): Future[Either[RestError, AnnotationMeta]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*)
      .post(Json.toJson(ann)(fmt.restFormat)).map { response =>
      checkError(response).right.map(r => r.json.as[AnnotationMeta](rd.restReads))
    }
  }
}