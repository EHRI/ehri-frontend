package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.json.{RestResource, ClientConvertable, RestReadable}
import play.api.libs.json.Json

object TestJson {
    import java.sql.Timestamp
    import play.api.libs.functional.syntax._
    import play.api.libs.json._

    implicit val rds: Reads[Timestamp] = (__ \ "time").read[Long].map{ long => new Timestamp(long) }
    implicit val wrs: Writes[Timestamp] = (__ \ "time").write[Long].contramap{ (a: Timestamp) => a.getTime }
    implicit val fmt: Format[Timestamp] = Format(rds, wrs)

    val testTime = Json.obj("time" -> 123456789)
    assert(testTime.as[Timestamp] == new Timestamp(123456789))
}


/**
 * Created by mike on 23/06/13.
 */
trait ApiBase[MT] extends EntityController {

  implicit def resource: RestResource[MT]

  def getClientJson(id: String)(implicit rr: RestReadable[MT], cw: ClientConvertable[MT]) = userProfileAction.async {
      implicit maybeUser => implicit request =>
    backend.get[MT](id).map { tm =>
      Ok(Json.toJson(tm)(cw.clientFormat))
    }
  }
}
