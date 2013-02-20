package controllers

import play.api.mvc._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current
import base.{Authorizer,AuthController,LoginHandler}
import play.api.data.Form
import play.api.data.Forms._
import models.sql.OpenIDUser
import play.api.i18n.Messages
import org.mindrot.jbcrypt.BCrypt

object Application extends Controller with Auth with LoginLogout with Authorizer with AuthController {

  lazy val loginHandler: LoginHandler = current.plugin(classOf[LoginHandler]).get

  /**
   * Look up the 'show' page of a generic item id
   * @param id
   */
  def genericShow(id: String) = Action {
    NotImplemented
  }


  def index = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      Ok(views.html.index("Your new application is ready."))
    }
  }

  val emailForm = Form(single("email" -> email))

  def login = loginHandler.login
  def loginPost = loginHandler.loginPost
  def logout = loginHandler.logout
}