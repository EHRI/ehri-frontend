package controllers.generic

import play.api.mvc.Controller
import defines.{ContentTypes,EntityType}
import rest.Backend
import models.json.RestResource
import controllers.base.{ControllerHelpers, AuthController}

trait Generic[MT] extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  val contentType: ContentTypes.Value

  val backend: Backend

  implicit def resource: RestResource[MT]
}
