package controllers.core.auth.openid

import controllers.base.CoreActionBuilders
import models.{UserProfile, Account}
import play.api.libs.openid._
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import backend.{AnonymousUser, DataApi}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Result
import java.net.ConnectException
import scala.concurrent.Future
import controllers.core.auth.AccountHelpers

import scala.concurrent.Future

/**
 * OpenID login handler implementation.
 */
trait OpenIDLoginHandler extends AccountHelpers {

  self: Controller with CoreActionBuilders =>

  def dataApi: DataApi
  def accounts: auth.AccountManager
  def globalConfig: global.GlobalConfig
  def openId: OpenIdClient
  implicit def app: play.api.Application

  val attributes = Seq(
    "email" -> "http://schema.openid.net/contact/email",
    "axemail" -> "http://axschema.org/contact/email",
    "axname" -> "http://axschema.org/namePerson",
    "name" -> "http://openid.netdr/schema/media/spokenname",
    "firstname" -> "http://openid.net/schema/namePerson/first",
    "lastname" -> "http://openid.net/schema/namePerson/last",
    "friendly" -> "http://openid.net/schema/namePerson/friendly"
  )

  val openidForm = Form(single(
    "openid_identifier" -> nonEmptyText
  ) verifying("error.badUrl", url => utils.forms.isValidUrl(url)))

  case class OpenIDRequest[A](
    errorForm: Form[String],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  protected def OpenIdLoginAction(handler: Call) = new ActionBuilder[OpenIDRequest] {
    override def invokeBlock[A](request: Request[A], block: (OpenIDRequest[A]) => Future[Result]): Future[Result] = {
      implicit val r = request
      implicit val a = app
      try {
        val boundForm: Form[String] = openidForm.bindFromRequest
        boundForm.fold(
          error => {
            Logger.info("bad request " + error.toString)
            block(OpenIDRequest(error, request))
          }, openidUrl => {
            openId.redirectURL(
              openidUrl,
              handler.absoluteURL(globalConfig.https),
              attributes).map(url => Redirect(url))
              .recoverWith {
              case t: ConnectException =>
                Logger.warn("OpenID Login connect exception: {}", t)
                block(OpenIDRequest(boundForm
                  .withGlobalError(Messages("error.openId.url", openidUrl)), request))
              case t =>
                Logger.warn("OpenID Login argument exception: {}", t)
                block(OpenIDRequest(boundForm
                  .withGlobalError(Messages("error.openId.url", openidUrl)), request))
            }
          }
        )
      } catch {
        case _: Throwable => block(OpenIDRequest(openidForm
          .withGlobalError(Messages("error.openId")), request))
      }
    }
  }

  case class OpenIdCallbackRequest[A](
    formOrAccount: Either[Form[String],Account],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  protected def OpenIdCallbackAction = new ActionBuilder[OpenIdCallbackRequest] {
    override def invokeBlock[A](request: Request[A], block: (OpenIdCallbackRequest[A]) => Future[Result]): Future[Result] = {
      implicit val r = request
      implicit val a = app

      openId.verifiedId(request).flatMap { info =>

        // check if there's a user with the right id
        accounts.openId.findByUrl(info.id).flatMap {
          case Some(assoc) =>
            Logger.logger.info("User '{}' logged in via OpenId", assoc.user.get.id)
            block(OpenIdCallbackRequest(Right(assoc.user.get), request))
          case None =>
            val email = extractEmail(info.attributes)
              .getOrElse(sys.error("Unable to retrieve email info via OpenID"))

            val data = Map("name" -> extractName(info.attributes)
              .getOrElse(sys.error("Unable to retrieve name info via OpenID")))

            accounts.findByEmail(email).flatMap {
              case Some(account) => addAssociation(account, info, request).flatMap(block)
              case None => createUserAccount(email, info, data, request).flatMap(block)
            }
        }
      } recoverWith {
        case t => block(OpenIdCallbackRequest(
          Left(openidForm.withGlobalError("error.openId", t.getMessage)), request))
          .map(_.flashing("error" -> Messages("error.openId", t.getMessage)))
      }
    }
  }

  private def addAssociation[A](account: Account, info: UserInfo, request: Request[A]): Future[OpenIdCallbackRequest[A]] = {
    Logger.logger.info("User '{}' created OpenID association", account.id)
    for {
      _ <- accounts.openId.addAssociation(account.id, info.id)
    } yield OpenIdCallbackRequest(Right(account), request)
  }

  private def createUserAccount[A](email: String, info: UserInfo, data: Map[String, String], request: Request[A])(implicit app: play.api.Application): Future[OpenIdCallbackRequest[A]] = {
    implicit val apiUser = AnonymousUser
    for {
      up <- userDataApi.createNewUserProfile[UserProfile](data, groups = defaultPortalGroups)
      account <- accounts.create(Account(
        id = up.id,
        email = email.toLowerCase,
        verified = true,
        staff = false,
        active = true,
        allowMessaging = canMessage
      ))
      _ <- accounts.openId.addAssociation(account.id, info.id)
    } yield {
      Logger.logger.info("User '{}' created OpenID account", account.id)
      OpenIdCallbackRequest(Right(account), request)
    }
  }

  /**
   * Pick up the email from OpenID info. This may be stored in different
   * attributes depending on the provider.
   */
  private def extractEmail(attrs: Map[String, String]): Option[String] =
    attrs.get("email").orElse(attrs.get("axemail"))

  private def extractName(attrs: Map[String,String]): Option[String] = {
    val fullName = for {
      fn <- attrs.get("firstname")
      ln <- attrs.get("lastname")
    } yield s"$fn $ln"
    attrs.get("name").orElse(attrs.get("fullname")).orElse(fullName)
  }
}