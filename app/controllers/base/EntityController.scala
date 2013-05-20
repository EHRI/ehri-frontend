package controllers.base

import models.base.AccessibleEntity
import play.api.mvc.{Request, AnyContent, Controller}
import defines.{ContentType,EntityType}
import models.Entity

trait EntityController[T <: AccessibleEntity] extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  val contentType: ContentType.Value

  final val LOG_MESSAGE_PARAM = "logMessage"

  def getLogMessage(implicit request: Request[AnyContent]) = {
    import play.api.data.Form
    import play.api.data.Forms._
    Form(single(LOG_MESSAGE_PARAM -> optional(nonEmptyText)))
      .bindFromRequest.value.getOrElse(None)
  }
}
