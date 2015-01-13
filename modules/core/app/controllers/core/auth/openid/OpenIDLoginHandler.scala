package controllers.core.auth.openid

import controllers.base.AuthController
import models.sql.OpenIDAssociation
import models.{UserProfile, Account, AccountDAO}
import play.api.libs.openid._
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import backend.{AnonymousUser, ApiUser, Backend}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Result
import play.api.i18n.Messages
import java.net.ConnectException
import controllers.core.auth.AccountHelpers

/**
 * OpenID login handler implementation.
 */
trait OpenIDLoginHandler extends AccountHelpers {

  self: Controller with AuthController =>

  val backend: Backend
  val globalConfig: global.GlobalConfig

  val userDAO: AccountDAO

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
      try {
        val boundForm: Form[String] = openidForm.bindFromRequest
        boundForm.fold(
          error => {
            Logger.info("bad request " + error.toString)
            block(OpenIDRequest(error, request))
          }, openidUrl => {
            OpenID.redirectURL(
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

      OpenID.verifiedId.flatMap { info =>

        // check if there's a user with the right id
        OpenIDAssociation.findByUrl(info.id).map { assoc =>

          // NOTE: If this user exists in the auth DB but not on the REST
          // server we have a bit of a problem at present...
          Logger.logger.info("User '{}' logged in via OpenId", assoc.user.get.id)
          block(OpenIdCallbackRequest(Right(assoc.user.get), request))
        } getOrElse {

          val email = extractEmail(info.attributes)
            .getOrElse(sys.error("Unable to retrieve email info via OpenID"))

          val data = Map("name" -> extractName(info.attributes)
            .getOrElse(sys.error("Unable to retrieve name info via OpenID")))

          userDAO.findByEmail(email).map { acc =>
            OpenIDAssociation.addAssociation(acc, info.id)
            Logger.logger.info("User '{}' created OpenID association", acc.id)
            block(OpenIdCallbackRequest(Right(acc), request))

          } getOrElse {
            implicit val apiUser = AnonymousUser
            backend.createNewUserProfile[UserProfile](data, groups = defaultPortalGroups).flatMap { up =>
              val account = userDAO.create(up.id, email.toLowerCase, verified = true,
                staff = false, allowMessaging = canMessage)
              OpenIDAssociation.addAssociation(account, info.id)
              Logger.logger.info("User '{}' created OpenID account", account.id)
              block(OpenIdCallbackRequest(Right(account), request))
            }
          }
        }
      } recoverWith {
        case t => block(OpenIdCallbackRequest(
          Left(openidForm.withGlobalError("error.openId", t.getMessage)), request))
          .map(_.flashing("error" -> Messages("error.openId", t.getMessage)))
      }
    }
  }

  /**
   * Pick up the email from OpenID info. This may be stored in different
   * attributes depending on the provider.
   */
  private def extractEmail(attrs: Map[String, String]): Option[String]
      = attrs.get("email").orElse(attrs.get("axemail"))

  private def extractName(attrs: Map[String,String]): Option[String] = {
    val fullName = for {
      fn <- attrs.get("firstname")
      ln <- attrs.get("lastname")
    } yield s"$fn $ln"
    attrs.get("name").orElse(attrs.get("fullname")).orElse(fullName)
  }
}