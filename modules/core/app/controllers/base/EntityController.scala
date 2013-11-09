package controllers.base

import play.api.mvc.{Request, AnyContent, Controller}
import defines.{ContentTypes,EntityType}
import models.UserProfile
import rest.{Backend, ApiUser}
import models.json.RestResource

trait EntityController[MT] extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  val contentType: ContentTypes.Value

  val backend: Backend

  implicit def resource: RestResource[MT]
}
