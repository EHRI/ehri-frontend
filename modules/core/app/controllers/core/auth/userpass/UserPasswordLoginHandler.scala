package controllers.core.auth.userpass

import auth.HashedPassword
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
import controllers.base.AuthController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait UserPasswordLoginHandler {

  self: Controller with AuthController with LoginLogout =>

  val userDAO: auth.AccountManager

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
          userDAO.authenticate(email, pw).flatMap {
            case Some(account) =>
              Logger.logger.info("User '{}' logged in via password", account.id)
              block(UserPasswordLoginRequest(Right(account), request))
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
            userDAO.findByEmail(email).flatMap {
              case Some(account) =>
                val uuid = UUID.randomUUID()
                for {
                  _ <- userDAO.createResetToken(account.id, uuid)
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

          (for {
            account <- request.user.account
            hashedPw <- account.password if hashedPw.check(current)
          } yield userDAO.update(account.copy(password = Some(hashedPw))).map { updated =>
            ChangePasswordRequest(None, request.user, request)
          }) getOrElse {
            immediate(ChangePasswordRequest(
              Some(form.withGlobalError("login.error.badUsernameOrPassword")),
              request.user, request))
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
          userDAO.findByResetToken(token).flatMap {
            case Some(account) =>
              for {
                _ <- userDAO.expireTokens(account.id)
                _ <- userDAO.setPassword(account.id, HashedPassword.fromPlain(pw))
              } yield ResetPasswordRequest(Right(account), request.userOpt, request)
            case None => immediate(ResetPasswordRequest(
              Left(form.withGlobalError("login.error.badResetToken")), request.userOpt, request))
          }
      })
    }
  }
}
