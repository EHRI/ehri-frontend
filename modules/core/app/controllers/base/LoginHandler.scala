package controllers.base

import play.api.mvc._
import jp.t2v.lab.play2.auth.{AsyncAuth, LoginLogout}
import play.api.libs.concurrent.Execution.Implicits._

trait LoginHandler extends Controller with AsyncAuth with LoginLogout with Authorizer {

  implicit val globalConfig: global.GlobalConfig

  def logout = optionalUserAction.async { implicit maybeUser => implicit request =>
    gotoLogoutSucceeded
  }
}