package controllers.base

import models.base.AccessibleEntity
import play.api.mvc.Controller
import defines.EntityType
import models.Entity


trait EntityController[T <: AccessibleEntity] extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  def builder: Entity => T
}
