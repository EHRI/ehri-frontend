package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import models.json.{RestResource, ClientConvertable, RestReadable}
import play.api.libs.json.Json

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Api[MT] extends Generic[MT] {
  def getClientJson(id: String)(implicit rr: RestReadable[MT], rs: RestResource[MT], cw: ClientConvertable[MT]) = userProfileAction.async {
      implicit maybeUser => implicit request =>
    backend.get[MT](id).map { tm =>
      Ok(Json.toJson(tm)(cw.clientFormat))
    }
  }
}
