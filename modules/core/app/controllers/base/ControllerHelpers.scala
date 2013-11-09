package controllers.base

import scala.concurrent.Future
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import rest._
import java.net.ConnectException
import models.UserProfile
import play.api.Play.current
import play.api.libs.json.Json
import global.{MenuConfig, GlobalConfig}
import scala.concurrent.Future.{successful => immediate}
import scala.Some
import scala.Some
import play.api.mvc.SimpleResult

object ControllerHelpers {
  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH")
      .map(_.toUpperCase() == "XMLHTTPREQUEST").getOrElse(false)
}

trait ControllerHelpers {
  this: Controller with AuthController =>

  implicit val globalConfig: GlobalConfig

  /**
   * Some actions **require** a user is logged in.
   * However the main templates assume it is optional. This helper
   * to put an optional user in scope for template rendering
   * when there's definitely one defined.
   */
  implicit def userOpt(implicit user: UserProfile): Option[UserProfile] = Some(user)

  /**
   * Issue a warning about database maintenance when a "dbmaintenance"
   * file is present in the app root and the DB is offline.
   * @return
   */
  def dbMaintenance: Boolean = new java.io.File("dbmaintenance").exists()

  /**
   * Extract a log message from an incoming request params
   */
  final val LOG_MESSAGE_PARAM = "logMessage"

  def getLogMessage(implicit request: Request[AnyContent]) = {
    import play.api.data.Form
    import play.api.data.Forms._
    Form(single(LOG_MESSAGE_PARAM -> optional(nonEmptyText)))
      .bindFromRequest.value.getOrElse(None)
  }


  /**
   * Check if a request is Ajax.
   */
  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH")
      .map(_.toUpperCase() == "XMLHTTPREQUEST").getOrElse(false)

  /**
   * Get a complete list of possible groups
   */
  object getGroups {
    def async(f: Seq[(String,String)] => Future[SimpleResult])(implicit userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
      rest.RestHelpers.getGroupList.flatMap { groups =>
        f(groups)
      }
    }

    def apply(f: Seq[(String,String)] => SimpleResult)(implicit userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
      async(f.andThen(t => immediate(t)))
    }
  }

  /**
   * Get a list of users and groups.
   */
  object getUsersAndGroups {
    def async(f: Seq[(String,String)] => Seq[(String,String)] => Future[SimpleResult])(
      implicit userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
      // TODO: Handle REST errors

      for {
        users <- rest.RestHelpers.getUserList
        groups <- rest.RestHelpers.getGroupList
        r <- f(users)(groups)
      } yield r
    }

    def apply(f: Seq[(String,String)] => Seq[(String,String)] => SimpleResult)(
      implicit userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }

  /**
   * Join params into a query string
   */
  def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map { case (key, vals) => {
      vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))
    }}.flatten.mkString("&")
  }
}