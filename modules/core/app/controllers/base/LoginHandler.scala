package controllers.base

import play.api.mvc._
import jp.t2v.lab.play2.auth.{AuthActionBuilders, AsyncAuth, LoginLogout}
import play.api.libs.concurrent.Execution.Implicits._

trait LoginHandler extends Controller with AsyncAuth with LoginLogout with AuthConfigImpl with AuthActionBuilders {
  def logout = OptionalAuthAction.async { implicit authRequest =>
    gotoLogoutSucceeded
  }
}