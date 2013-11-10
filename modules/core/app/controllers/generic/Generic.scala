package controllers.generic

import play.api.mvc.Controller
import defines.ContentTypes
import rest.Backend
import models.json.RestResource
import controllers.base.{ControllerHelpers, AuthController}

trait Generic[MT] extends Controller with AuthController with ControllerHelpers {
  implicit val resource: RestResource[MT]
  val backend: Backend
  val contentType: ContentTypes.Value
}
