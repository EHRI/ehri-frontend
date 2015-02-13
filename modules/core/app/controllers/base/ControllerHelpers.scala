package controllers.base

import backend.rest.RestHelpers
import models.UserProfile
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


trait ControllerHelpers {

  implicit def globalConfig: global.GlobalConfig

  /**
   * Issue a warning about database maintenance when a "dbmaintenance"
   * file is present in the app root and the DB isr offline.
   * @return
   */
  def dbMaintenance: Boolean = new java.io.File("dbmaintenance").exists()

  /**
   * Extract a log message from an incoming request params
   */
  final val LOG_MESSAGE_PARAM = "logMessage"

  def getLogMessage(implicit request: Request[_]) = {
    import play.api.data.Form
    import play.api.data.Forms._
    Form(single(LOG_MESSAGE_PARAM -> optional(nonEmptyText)))
      .bindFromRequest.value.getOrElse(None)
  }


  /**
   * Check if a request is Ajax.
   */
  def isAjax(implicit request: RequestHeader): Boolean = utils.isAjax

  /**
   * Get a complete list of possible groups
   */
  object getGroups {
    def async(f: Seq[(String,String)] => Future[Result])(implicit userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      RestHelpers.getGroupList.flatMap { groups =>
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
        users <- RestHelpers.getUserList
        groups <- RestHelpers.getGroupList
        r <- f(users)(groups)
      } yield r
    }

    def apply(f: Seq[(String,String)] => Seq[(String,String)] => Result)(
      implicit userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }
}