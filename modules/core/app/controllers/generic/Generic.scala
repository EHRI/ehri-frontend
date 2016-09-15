package controllers.generic

import controllers.base.CoreActionBuilders
import global.GlobalConfig
import play.api.libs.concurrent.Execution.Implicits._
import backend.DataApi
import backend.rest.{Constants, DataHelpers}
import models.UserProfile
import play.api.mvc.{Request, RequestHeader, Result}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
 * Base trait for controllers that deal with the dataApi.
 */
trait Generic extends CoreActionBuilders {

  def globalConfig: GlobalConfig

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
