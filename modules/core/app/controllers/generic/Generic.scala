package controllers.generic

import play.api.mvc.{Request, AnyContent, Controller}
import defines.{ContentTypes,EntityType}
import models.UserProfile
import rest.{Backend, ApiUser}
import models.json.RestResource
import controllers.base.{ControllerHelpers, AuthController}

trait Generic[MT] extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  val contentType: ContentTypes.Value

  val backend: Backend

  implicit def resource: RestResource[MT]
}
