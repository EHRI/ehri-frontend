package controllers.core.auth.userpass

import models.{Account, AccountDAO}
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import play.api.Logger
import jp.t2v.lab.play2.auth.LoginLogout
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import play.api.mvc.SimpleResult

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait UserPasswordLoginHandler {

  self: Controller with LoginLogout =>

  val userDAO: AccountDAO

  val passwordLoginForm = Form(
    tuple(
      "email" -> email,
      "password" -> nonEmptyText
    )
  )

  protected val changePasswordForm = Form(
    tuple(
      "current" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.passwordsDoNotMatch", f => f match {
      case (_, pw, pwc) => pw == pwc
    })
  )

  protected val forgotPasswordForm = Form(Forms.single("email" -> email))


  object loginPostAction {

    def async(f: Either[Form[(String,String)], Account] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      Action.async { implicit request =>
        passwordLoginForm.bindFromRequest.fold(
          errorForm => f(Left(errorForm))(request),
          data => {
            val (email, pw) = data
            userDAO.authenticate(email, pw).map { account =>
              Logger.logger.info("User '{}' logged in via password", account.id)
              f(Right(account))(request)
            } getOrElse {
              f(Left(passwordLoginForm.withGlobalError("login.badUsernameOrPassword")))(request)
            }
          }
        )
      }
    }

    def apply(f: Either[Form[(String,String)], Account] => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }
}
