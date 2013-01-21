package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import play.api.libs.json.{ JsArray, JsValue }
import defines.EntityType
import models.{Annotation, Entity, UserProfile}
import play.api.libs.json.Json
import java.net.ConnectException
import models.base.Persistable
import models.forms.AnnotationF


/**
 * Data Access Object for fetching annotation data.
 *
 * @param userProfile
 */
case class AnnotationDAO(val userProfile: Option[UserProfile] = None) extends RestDAO {

  implicit val entityReads = Entity.entityReads
  implicit val entityPageReads = PageReads.pageReads
  import EntityDAO._
  import play.api.http.Status._

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.Annotation)

  def authHeaders: Map[String, String] = userProfile match {
    case Some(up) => (headers + (AUTH_HEADER_NAME -> up.id))
    case None => headers
  }

  def getFor(id: String): Future[Either[RestError, Map[String,List[Annotation]]]] = {

    WS.url(enc(requestUrl, "for/%s?limit=1000".format(id)))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Map[String, List[models.Entity]]].fold(
          valid = {
            map => map.map { case(s, lst) =>
              (s, lst.map(Annotation(_)))
            }
          },
          invalid = { e =>
            println(r.json)
            sys.error("Unable to decode list result: " + e.toString)
          }
        )
      }
    }
  }

  def create(id: String, ann: AnnotationF): Future[Either[RestError, Annotation]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*)
      .post(ann.toJson).map { response =>
      checkError(response).right.map(r => Annotation(jsonToEntity(r.json)))
    }
  }

  def link(id: String, src: String, ann: AnnotationF): Future[Either[RestError, Annotation]] = {
    WS.url(enc(requestUrl, id, src)).withHeaders(authHeaders.toSeq: _*)
      .post(ann.toJson).map { response =>
      checkError(response).right.map(r => Annotation(jsonToEntity(r.json)))
    }
  }
}