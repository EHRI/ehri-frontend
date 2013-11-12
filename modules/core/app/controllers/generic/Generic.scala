package controllers.generic

import play.api.mvc.Controller
import defines.ContentTypes
import models.json.RestResource
import controllers.base.{ControllerHelpers, AuthController}
import backend.Backend

trait Generic[MT] extends Controller with AuthController with ControllerHelpers {
  implicit val resource: RestResource[MT]
  val backend: Backend
  val contentType: ContentTypes.Value
}
