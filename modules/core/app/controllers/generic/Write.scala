package controllers.generic

import controllers.base.CoreActionBuilders
import global.GlobalConfig
import backend.rest.Constants
import play.api.mvc.Request


/**
 * Base trait for controllers that deal with the dataApi.
 */
trait Write extends CoreActionBuilders {

  protected def globalConfig: GlobalConfig

  /**
   * Extract a log message from an incoming request params
   */
  def getLogMessage(implicit request: Request[_]) = {
    import play.api.data.Form
    import play.api.data.Forms._
    Form(single(Constants.LOG_MESSAGE_PARAM -> optional(nonEmptyText(maxLength = globalConfig.logMessageMaxLength))))
      .bindFromRequest.value.getOrElse(None)
  }
}
