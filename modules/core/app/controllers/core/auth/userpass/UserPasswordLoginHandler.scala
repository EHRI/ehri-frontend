package controllers.core.auth.userpass

import play.api.libs.concurrent.Execution.Implicits._
import models.{UserProfile, Account, AccountDAO}
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import play.api.Logger
import jp.t2v.lab.play2.auth.LoginLogout
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import play.api.Play._
import utils.forms._
import play.api.mvc.SimpleResult
import java.util.UUID
import controllers.base.AuthController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait UserPasswordLoginHandler {

  self: Controller with AuthController with LoginLogout =>

  val userDAO: AccountDAO

  val passwordLoginForm = Form(
    tuple(
      "email" -> email,
      "password" -> nonEmptyText
    )
  )

  val changePasswordForm = Form(
    tuple(
      "current" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.passwordsDoNotMatch", f => f match {
      case (_, pw, pwc) => pw == pwc
    })
  )

  val resetPasswordForm = Form(
    tuple(
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.passwordsDoNotMatch", f => f match {
      case (pw, pwc) => pw == pwc
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

  def sendResetEmail(email: String, uuid: UUID)(implicit request: RequestHeader) {
    import com.typesafe.plugin._
    use[MailerPlugin].email
      .setSubject("EHRI Password Reset")
      .setRecipient(email)
      .setFrom("EHRI Password Reset <noreply@ehri-project.eu>")
      .send(views.txt.admin.mail.forgotPassword(uuid).body,
      views.html.admin.mail.forgotPassword(uuid).body)
  }

  object forgotPasswordPostAction {
    def async(f: Either[Form[String],Boolean] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      Action.async { implicit request =>
        checkRecapture.flatMap { ok =>
          if (!ok) {
            val form = forgotPasswordForm.withGlobalError("error.badRecaptcha")
            f(Left(form))(request)
          } else {
            forgotPasswordForm.bindFromRequest.fold({ errForm =>
              f(Left(errForm))(request)
            }, { email =>
              userDAO.findByEmail(email).map { account =>
                val uuid = UUID.randomUUID()
                account.createResetToken(uuid)
                sendResetEmail(account.email, uuid)
                f(Right(true))(request)
              }.getOrElse {
                val form = forgotPasswordForm.withError("email", "error.emailNotFound")
                f(Left(form))(request)
              }
            })
          }
        }
      }
    }

    def apply(f: Either[Form[String],Boolean] => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }

  /**
   * Store a changed password.
   * @return
   */
  object changePasswordPostAction {
    def async(f: Either[Form[(String,String,String)],Boolean] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      userProfileAction.async { implicit userOpt => implicit request =>
        changePasswordForm.bindFromRequest.fold(
          errorForm => f(Left(errorForm))(userOpt)(request),
          data => {
            val (current, newPw, _) = data

            (for {
              user <- userOpt
              account <- user.account
              hashedPw <- account.password if Account.checkPassword(current, hashedPw)
            } yield {
              account.updatePassword(Account.hashPassword(newPw))
              f(Right(true))(userOpt)(request)
            }) getOrElse {
              f(Right(false))(userOpt)(request)
            }
          }
        )
      }
    }

    def apply(f: Either[Form[(String,String,String)],Boolean] => Option[UserProfile] =>Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(f.andThen(_.andThen(_.andThen(t => immediate(t)))))
    }
  }

  object resetPasswordPostAction {
    def async(token: String)(f: Either[Form[(String,String)],Boolean] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      Action.async { implicit request =>
        resetPasswordForm.bindFromRequest.fold({ errForm =>
          f(Left(errForm))(request)
        }, { case (pw, _) =>
          userDAO.findByResetToken(token).map { account =>
            account.updatePassword(Account.hashPassword(pw))
            account.expireTokens()
            f(Right(true))(request)
          }.getOrElse {
            f(Right(false))(request)
          }
        })
      }
    }

    def apply(token: String)(f: Either[Form[(String,String)],Boolean] => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(token)(f.andThen(_.andThen(t => immediate(t))))
    }
  }
}
