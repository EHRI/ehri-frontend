package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import backend.{BackendReadable, BackendResource}
import controllers.generic.Generic
import client.json.ClientWriteable

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Api[MT] extends Generic {
  def getClientJson(id: String)(implicit rr: BackendReadable[MT], rs: BackendResource[MT], cw: ClientWriteable[MT]) = OptionalUserAction.async {
      implicit request =>
    userBackend.get[MT](id).map { tm =>
      Ok(Json.toJson(tm)(cw.clientFormat))
    }
  }
}
