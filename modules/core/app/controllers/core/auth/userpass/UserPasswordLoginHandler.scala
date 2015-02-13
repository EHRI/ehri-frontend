package controllers.core.auth.userpass

import auth.HashedPassword
import controllers.core.auth.AccountHelpers
import play.api.libs.concurrent.Execution.Implicits._
import models.{UserProfile, Account}
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
import controllers.base.CoreActionBuilders

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait UserPasswordLoginHandler {

  self: Controller with CoreActionBuilders with LoginLogout with AccountHelpers =>

  import play.api.Play.current

  val accounts: auth.AccountManager

  val passwordLoginForm = Form(
    tuple(
      "email" -> email,
      "password" -> nonEmptyText
    )
  )

  def changePasswordForm = Form(
    tuple(
      "current" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = minPasswordLength),
      "confirm" -> nonEmptyText(minLength = minPasswordLength)
    ) verifying("login.error.passwordsDoNotMatch", passwords => passwords._2 == passwords._3)
  )

  def resetPasswordForm = Form(
    tuple(
      "password" -> nonEmptyText(minLength = minPasswordLength),
      "confirm" -> nonEmptyText(minLength = minPasswordLength)
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
          accounts.authenticateByEmail(email, pw).flatMap {
            case Some(account) =>
              // Legacy accounts have an MD5 password encoded via BCrypt, so
              // we need to re-save this and untag them as legacy.
              if (account.isLegacy) {
                Logger.logger.info("Updating legacy account for user: {}", account.id)
                accounts.update(account = account.copy(
                  password = Some(HashedPassword.fromPlain(pw)),
                  isLegacy = false
                )).flatMap { updated =>
                  block(UserPasswordLoginRequest(Right(updated), request))
                }
              } else {
                Logger.logger.info("User logged in via password: {}", account.id)
                block(UserPasswordLoginRequest(Right(account), request))
              }
            case None =>
              block(UserPasswordLoginRequest(Left(boundForm
                .withGlobalError("login.error.badUsernameOrPassword")), request))
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
      checkRecapture.flatMap {
        case false =>
          val form = forgotPasswordForm.withGlobalError("error.badRecaptcha")
          immediate(ForgotPasswordRequest(Left(form), request.userOpt, request))
        case true =>
          forgotPasswordForm.bindFromRequest.fold({ errForm =>
            immediate(ForgotPasswordRequest(Left(errForm), request.userOpt, request))
          }, { email =>
            accounts.findByEmail(email).flatMap {
              case Some(account) =>
                val uuid = UUID.randomUUID()
                for {
                  _ <- accounts.createToken(account.id, uuid, isSignUp = false)
                } yield ForgotPasswordRequest(Right((account, uuid)), request.userOpt, request)
              case None =>
                val form = forgotPasswordForm.withError("email", "error.emailNotFound")
                immediate(ForgotPasswordRequest(Left(form), request.userOpt, request))
            }
          })
      }
    }
  }

  case class ChangePasswordRequest[A](
    errForm: Option[Form[(String,String,String)]],
    user: UserProfile,
    request: Request[A]
  ) extends WrappedRequest[A](request)
  
  protected def ChangePasswordAction = WithUserAction andThen new ActionTransformer[WithUserRequest, ChangePasswordRequest] {
    override protected def transform[A](request: WithUserRequest[A]): Future[ChangePasswordRequest[A]] = {
      implicit val r = request
      val form = changePasswordForm.bindFromRequest
      form.fold(
        errorForm => immediate(ChangePasswordRequest(Some(errorForm), request.user, request)),
        data => {
          val (current, newPw, _) = data
          accounts.authenticateById(request.user.id, current).flatMap {
            case Some(account) => accounts.update(account.copy(
              password = Some(HashedPassword.fromPlain(newPw)),
              isLegacy = false
            )).map { _ =>
              ChangePasswordRequest(None, request.user, request)
            }
            case None =>
              immediate(ChangePasswordRequest(
                Some(form.withGlobalError("login.error.badUsernameOrPassword")), request.user, request))
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
    override protected def transform[A](request: OptionalUserRequest[A]): Future[ResetPasswordRequest[A]] = {
      implicit val r = request
      val form: Form[(String, String)] = resetPasswordForm.bindFromRequest
      form.fold(
        errForm => immediate(ResetPasswordRequest(Left(errForm), request.userOpt, request)),
        { case (pw, _) =>
          accounts.findByToken(token).flatMap {
            case Some(account) =>
              for {
                _ <- accounts.expireTokens(account.id)
                _ <- accounts.update(account.copy(
                  password = Some(HashedPassword.fromPlain(pw)),
                  isLegacy = false
                ))
              } yield ResetPasswordRequest(Right(account), request.userOpt, request)
            case None => immediate(ResetPasswordRequest(
              Left(form.withGlobalError("login.error.badResetToken")), request.userOpt, request))
          }
      })
    }
  }
}
