package controllers.generic

import controllers.base.CoreActionBuilders
import global.GlobalConfig
import play.api.libs.concurrent.Execution.Implicits._
import backend.Backend
import backend.rest.{Constants, RestHelpers}
import models.UserProfile
import play.api.mvc.{Request, RequestHeader, Result}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
 * Base trait for controllers that deal with the backend.
 */
trait Generic extends CoreActionBuilders with RestHelpers {

  def globalConfig: GlobalConfig
  def backend: Backend

  /**
   * Extract a log message from an incoming request params
   */
  def getLogMessage(implicit request: Request[_]) = {
    import play.api.data.Form
    import play.api.data.Forms._
    Form(single(Constants.LOG_MESSAGE_PARAM -> optional(nonEmptyText(maxLength = globalConfig.logMessageMaxLength))))
      .bindFromRequest.value.getOrElse(None)
  }

  /**
   * Get a complete list of possible groups
   */
  object getGroups {
    def async(f: Seq[(String,String)] => Future[Result])(implicit userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      getGroupList.flatMap { groups =>
        f(groups)
      }
    }

    def apply(f: Seq[(String,String)] => Result)(implicit userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      async(f.andThen(t => immediate(t)))
    }
  }

  /**
   * Get a list of users and groups.
   */
  object getUsersAndGroups {
    def async(f: Seq[(String,String)] => Seq[(String,String)] => Future[Result])(
      implicit userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      for {
        users <- getUserList
        groups <- getGroupList
        r <- f(users)(groups)
      } yield r
    }

    def apply(f: Seq[(String,String)] => Seq[(String,String)] => Result)(
      implicit userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }

}
