package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import models.json.{ClientConvertable, RestReadable}

import play.api.libs.json.Json
import defines.EntityType

/**
 * Created by mike on 23/06/13.
 */
trait ApiBase[TM] extends Controller with ControllerHelpers with AuthController {

  val entityType: EntityType.Value

  def getClientJson(id: String)(implicit rr: RestReadable[TM], cw: ClientConvertable[TM]) = userProfileAction {
      implicit maybeUser => implicit request =>
    AsyncRest {
      rest.EntityDAO(entityType, maybeUser).getJson(id).map { res =>
        res.right.map { tm =>
          tm.fold(
            invalid = { err =>
              BadRequest(err.toString) // Should be 500!
            },
            valid = { item =>
              Ok(Json.toJson(item)(cw.clientFormat))
            }
          )
        }
      }
    }
  }
}
