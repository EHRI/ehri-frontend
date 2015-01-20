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
    ) verifying("login.error.passwordsDoNotMatch", passwords => passwords._2 == passwords._3)
  )

  val resetPasswordForm = Form(
    tuple(
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.error.passwordsDoNotMatch", pc => pc._1 == pc._2)
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
    userOpt: Option[UserProfile],
    request: Request[A]                                     
  ) extends WrappedRequest[A](request)
    with WithOptionalUser
  
  protected def ForgotPasswordAction = OptionalUserAction andThen new ActionTransformer[OptionalUserRequest, ForgotPasswordRequest] {
    override protected def transform[A](request: OptionalUserRequest[A]): Future[ForgotPasswordRequest[A]] = {
      implicit val r = request
      checkRecapture.map { ok =>
        if (!ok) {
          val form = forgotPasswordForm.withGlobalError("error.badRecaptcha")
          ForgotPasswordRequest(Left(form), request.userOpt, request)
        } else {
          forgotPasswordForm.bindFromRequest.fold({ errForm =>
            ForgotPasswordRequest(Left(errForm), request.userOpt, request)
          }, { email =>
            userDAO.findByEmail(email).map { account =>
              val uuid = UUID.randomUUID()
              account.createResetToken(uuid)
              ForgotPasswordRequest(Right((account, uuid)), request.userOpt, request)
            }.getOrElse {
              val form = forgotPasswordForm.withError("email", "error.emailNotFound")
              ForgotPasswordRequest(Left(form), request.userOpt, request)
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
            hashedPw <- account.password if hashedPw.check(current)
          } yield {
            account.setPassword(userDAO.hashPassword(newPw))
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
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def ResetPasswordAction(token: String) = OptionalUserAction andThen new ActionTransformer[OptionalUserRequest, ResetPasswordRequest] {
    override protected def transform[A](request: OptionalUserRequest[A]): Future[ResetPasswordRequest[A]] = immediate {
      implicit val r = request
      val form: Form[(String, String)] = resetPasswordForm.bindFromRequest
      form.fold(
        errForm => ResetPasswordRequest(Left(errForm), request.userOpt, request),
        { case (pw, _) =>
        userDAO.findByResetToken(token).map { account =>
          account.setPassword(userDAO.hashPassword(pw))
          account.expireTokens()
          ResetPasswordRequest(Right(account), request.userOpt, request)
        }.getOrElse {
          ResetPasswordRequest(
            Left(form.withGlobalError("login.error.badResetToken")), request.userOpt, request)
        }
      })
    }
  }
}
