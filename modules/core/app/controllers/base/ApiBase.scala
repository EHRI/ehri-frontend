package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import models.json.{ClientConvertable, RestReadable}

import play.api.libs.json.{JsError, Json}
import defines.EntityType

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
trait ApiBase[TM] extends EntityController {

  def getClientJson(id: String)(implicit rr: RestReadable[TM], cw: ClientConvertable[TM]) = userProfileAction {
      implicit maybeUser => implicit request =>
    AsyncRest {
      rest.EntityDAO(entityType, maybeUser).get(id).map { res =>
        res.right.map { tm =>
          Ok(Json.toJson(tm)(cw.clientFormat))
        }
      }
    }
  }
}
