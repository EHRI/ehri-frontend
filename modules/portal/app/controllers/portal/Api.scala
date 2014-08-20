package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import models.json.ClientWriteable
import play.api.libs.json.Json
import backend.{BackendReadable, BackendResource}
import controllers.generic.Generic

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Api[MT] extends Generic[MT] {
  def getClientJson(id: String)(implicit rr: BackendReadable[MT], rs: BackendResource[MT], cw: ClientWriteable[MT]) = userProfileAction.async {
      implicit maybeUser => implicit request =>
    backend.get[MT](id).map { tm =>
      Ok(Json.toJson(tm)(cw.clientFormat))
    }
  }
}
