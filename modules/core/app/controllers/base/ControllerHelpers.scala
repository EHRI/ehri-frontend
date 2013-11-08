package controllers.base

import scala.concurrent.Future
import play.api.mvc.RequestHeader
import play.api.mvc.SimpleResult
import play.api.libs.concurrent.Execution.Implicits._
import rest._
import play.api.mvc.Controller
import play.api.mvc.AsyncResult
import java.net.ConnectException
import models.UserProfile
import play.api.Play.current
import play.api.libs.json.Json
import global.{MenuConfig, GlobalConfig}
import scala.concurrent.Future.{successful => immediate}

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
   * Object that handles event hooks
   */
  //implicit val eventHandler = globalConfig.eventHandler

  /**
   * Issue a warning about database maintenance when a "dbmaintenance"
   * file is present in the app root and the DB is offline.
   * @return
   */
  def dbMaintenance: Boolean = new java.io.File("dbmaintenance").exists()

  /**
   * Check if a request is Ajax.
   */
  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH")
      .map(_.toUpperCase() == "XMLHTTPREQUEST").getOrElse(false)

  /**
   * Get a complete list of possible groups
   * @param f
   * @param userOpt
   * @param request
   * @return
   */
  def getGroups(f: Seq[(String,String)] => SimpleResult)(implicit userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
    for {
      groups <- rest.RestHelpers.getGroupList
    } yield {
      f(groups)
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