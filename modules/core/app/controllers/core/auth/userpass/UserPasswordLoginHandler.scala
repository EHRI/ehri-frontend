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
import utils.forms._
import play.api.mvc.Result
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
    ) verifying("login.error.passwordsDoNotMatch", f => f match {
      case (_, pw, pwc) => pw == pwc
    })
  )

  val resetPasswordForm = Form(
    tuple(
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.error.passwordsDoNotMatch", f => f match {
      case (pw, pwc) => pw == pwc
    })
  )

  protected val forgotPasswordForm = Form(Forms.single("email" -> email))

  case class UserPasswordLoginRequest[A](
    formOrAccount: Either[Form[(String,String)], Account],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  protected def UserPasswordLoginAction = new ActionBuilder[UserPasswordLoginRequest] {
    override def invokeBlock[A](request: Request[A], block: (UserPasswordLoginRequest[A]) => Future[Result]): Future[Result] = {
      implicit val r = request
      val boundForm = passwordLoginForm.bindFromRequest
      boundForm.fold(
        errorForm => block(UserPasswordLoginRequest(Left(errorForm), request)),
        data => {
          val (email, pw) = data
          userDAO.authenticate(email, pw).map { account =>
            Logger.logger.info("User '{}' logged in via password", account.id)
            block(UserPasswordLoginRequest(Right(account), request))
          } getOrElse {
            block(UserPasswordLoginRequest(Left(boundForm.withGlobalError("login.error.badUsernameOrPassword")), request))
          }
        }
      )
    }
  }

  case class ForgotPasswordRequest[A](
    formOrAccount: Either[Form[String],(Account,UUID)],
    request: Request[A]                                     
  ) extends WrappedRequest[A](request)
  
  protected def ForgotPasswordAction = new ActionBuilder[ForgotPasswordRequest] {
    override def invokeBlock[A](request: Request[A], block: (ForgotPasswordRequest[A]) => Future[Result]): Future[Result] = {
      implicit val r = request
      checkRecapture.flatMap { ok =>
        if (!ok) {
          val form = forgotPasswordForm.withGlobalError("error.badRecaptcha")
          block(ForgotPasswordRequest(Left(form), request))
        } else {
          forgotPasswordForm.bindFromRequest.fold({ errForm =>
            block(ForgotPasswordRequest(Left(errForm), request))
          }, { email =>
            userDAO.findByEmail(email).map { account =>
              val uuid = UUID.randomUUID()
              account.createResetToken(uuid)
              block(ForgotPasswordRequest(Right((account, uuid)), request))
            }.getOrElse {
              val form = forgotPasswordForm.withError("email", "error.emailNotFound")
              block(ForgotPasswordRequest(Left(form), request))
            }
          })
        }
      }
    }
  }

  case class ChangePasswordRequest[A](
    errForm: Option[Form[(String,String,String)]],
    user: UserProfile,
    request: Request[A]
  ) extends WrappedRequest[A](request)
  
  protected def ChangePasswordAction = WithUserAction andThen new ActionTransformer[WithUserRequest, ChangePasswordRequest] {
    override protected def transform[A](request: WithUserRequest[A]): Future[ChangePasswordRequest[A]] = immediate {
      implicit val r = request
      val form = changePasswordForm.bindFromRequest
      form.fold(
        errorForm => ChangePasswordRequest(Some(errorForm), request.user, request),
        data => {
          val (current, newPw, _) = data

          (for {
            account <- request.user.account
            hashedPw <- account.password if Account.checkPassword(current, hashedPw)
          } yield {
            account.setPassword(Account.hashPassword(newPw))
            println("OKAY!")
            ChangePasswordRequest(None, request.user, request)
          }) getOrElse {
            ChangePasswordRequest(
              Some(form.withGlobalError("login.error.badUsernameOrPassword")),
              request.user, request)
          }
        }
      )
    }
  }

  case class ResetPasswordRequest[A](
    formOrAccount: Either[Form[(String,String)], Account],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  protected def ResetPasswordAction(token: String) = new ActionBuilder[ResetPasswordRequest] {
    override def invokeBlock[A](request: Request[A], block: (ResetPasswordRequest[A]) => Future[Result]): Future[Result] = {
      implicit val r = request
      val form: Form[(String, String)] = resetPasswordForm.bindFromRequest
      form.fold(
        errForm => block(ResetPasswordRequest(Left(errForm), request)),
        { case (pw, _) =>
        userDAO.findByResetToken(token).map { account =>
          account.setPassword(Account.hashPassword(pw))
          account.expireTokens()
          block(ResetPasswordRequest(Right(account), request))
        }.getOrElse {
          block(ResetPasswordRequest(
            Left(form.withGlobalError("login.error.badResetToken")), request))
        }
      })
    }
  }
}
