package controllers.base

import models.base.AnyModel
import play.api.mvc.{Request, AnyContent, Controller}
import defines.{ContentTypes,EntityType}

trait EntityController extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  val contentType: ContentTypes.Value

  final val LOG_MESSAGE_PARAM = "logMessage"

  def getLogMessage(implicit request: Request[AnyContent]) = {
    import play.api.data.Form
    import play.api.data.Forms._
    Form(single(LOG_MESSAGE_PARAM -> optional(nonEmptyText)))
      .bindFromRequest.value.getOrElse(None)
  }
}
