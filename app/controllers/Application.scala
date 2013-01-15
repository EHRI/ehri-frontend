package controllers

import play.api.mvc._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current
import base.{Authorizer,AuthController,LoginHandler}
import play.api.data.Form
import play.api.data.Forms._

object Application extends Controller with Auth with LoginLogout with Authorizer with AuthController {

  lazy val loginHandler: LoginHandler = current.plugin(classOf[LoginHandler]).get

  /**
   * Look up the 'show' page of a generic item id
   * @param id
   */
  def genericShow(id: String) = Action {
    NotImplemented
  }


  def index = userProfileAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.index("Your new application is ready."))
  }

  def test = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.test("Testing login here..."))
  }

  val emailForm = Form(single("email" -> email))

  def mailTest = optionalUserAction { implicit maybeUser => implicit request =>

    Ok(views.html.mailTest(emailForm, routes.Application.mailTestPost, maybeUser, request))

  }

  def mailTestPost = optionalUserAction { implicit maybeUser => implicit request =>
    emailForm.bindFromRequest.fold(
      hasErrors = { errorForm =>
        Ok(views.html.mailTest(errorForm, routes.Application.mailTestPost, maybeUser, request))
      },
      success = { email =>
        Ok("Got email: " + email)

        /*import play.api.Play.current
        import com.typesafe.plugin._
        val mail = use[MailerPlugin].email
        mail.setSubject("Testing the mailer")
        mail.addRecipient("Mike B <noreply@email.com>",email)
        mail.addFrom("Testing <noreply@email.com>")
        //sends both text and html
        mail.send( "Hello, mike!", "<html>Hello, mike!</html>")*/
        Ok("mail sent")

      }
    )
  }

  def login = loginHandler.login
  def loginPost = loginHandler.loginPost
  def logout = loginHandler.logout
}