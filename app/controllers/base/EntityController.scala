package controllers.base

import models.base.AccessibleEntity
import play.api.mvc.Controller
import defines.{ContentType,EntityType}
import models.Entity


trait EntityController[T <: AccessibleEntity] extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  implicit val contentType: ContentType.Value
  def builder: Entity => T
}
