package controllers.core.auth.openid

import models.sql.OpenIDAssociation
import models.{UserProfile, Account, AccountDAO}
import play.api.libs.openid._
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import backend.{ApiUser, Backend}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Result
import play.api.i18n.Messages
import utils.forms.isValidUrl
import java.net.ConnectException
import controllers.core.auth.AccountHelpers

/**
 * OpenID login handler implementation.
 */
trait OpenIDLoginHandler extends AccountHelpers {

  self: Controller =>

  val backend: Backend
  val globalConfig: global.GlobalConfig

  val userDAO: AccountDAO

  val attributes = Seq(
    "email" -> "http://schema.openid.net/contact/email",
    "axemail" -> "http://axschema.org/contact/email",
    "axname" -> "http://axschema.org/namePerson",
    "name" -> "http://openid.net/schema/media/spokenname",
    "firstname" -> "http://openid.net/schema/namePerson/first",
    "lastname" -> "http://openid.net/schema/namePerson/last",
    "friendly" -> "http://openid.net/schema/namePerson/friendly"
  )

  val openidForm = Form(single(
    "openid_identifier" -> nonEmptyText
  ) verifying("OpenID URL is invalid", f => f match  {
    case s => isValidUrl(s)
  }))

  object openIDLoginPostAction {
    def async(handler: Call)(f: Form[String] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      Action.async { implicit request =>
        try {
          val boundForm: Form[String] = openidForm.bindFromRequest
          boundForm.fold(
            error => {
              Logger.info("bad request " + error.toString)
              f(error)(request)
            }, openidUrl => {

              OpenID.redirectURL(
                openidUrl,
                handler.absoluteURL(globalConfig.https),
                attributes).map(url => Redirect(url))
                .recoverWith {
                case t: ConnectException => {
                  Logger.warn("OpenID Login connect exception: {}", t)
                  f(boundForm
                    .withGlobalError(Messages("error.openId.url", openidUrl)))(request)
                }
                case t => {
                  Logger.warn("OpenID Login argument exception: {}", t)
                  f(boundForm
                    .withGlobalError(Messages("error.openId.url", openidUrl)))(request)
                }
              }
            }
          )
        } catch {
          case _: Throwable => f(openidForm
            .withGlobalError(Messages("error.openId.url")))(request)
        }
      }
    }

    def apply(handler: Call)(f: Form[String] => Request[AnyContent] => Result): Action[AnyContent] = {
      async(handler)(f.andThen(_.andThen(t => immediate(t))))
    }
  }

  object openIDCallbackAction {
    def async(f: Either[Form[String],Account] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      Action.async { implicit request =>
        OpenID.verifiedId.flatMap { info =>

          // check if there's a user with the right id
          OpenIDAssociation.findByUrl(info.id).map { assoc =>
            // NOTE: If this user exists in the auth DB but not on the REST
            // server we have a bit of a problem at present...
            Logger.logger.info("User '{}' logged in via OpenId", assoc.user.get.id)
            f(Right(assoc.user.get))(request)
          } getOrElse {
            val email = extractEmail(info.attributes)
              .getOrElse(sys.error("Unable to retrieve email info via OpenID"))
            val data = Map("name" -> extractName(info.attributes)
              .getOrElse(sys.error("Unable to retrieve name info via OpenID")))
            userDAO.findByEmail(email).map { acc =>
              OpenIDAssociation.addAssociation(acc, info.id)
              Logger.logger.info("User '{}' created OpenID association", acc.id)
              f(Right(acc))(request)
            } getOrElse {
              implicit val apiUser = ApiUser()
              backend.createNewUserProfile(data, groups = defaultPortalGroups).flatMap { up =>
                val account = userDAO.create(up.id, email.toLowerCase, verified = true,
                  staff = false, allowMessaging = canMessage)
                OpenIDAssociation.addAssociation(account, info.id)
                Logger.logger.info("User '{}' created OpenID account", account.id)
                f(Right(account))(request)
              }
            }
          }
        } recoverWith {
          case t => f(Left(openidForm.withGlobalError("error.openId", t.getMessage)))(request)
            .map(_.flashing("error" -> Messages("error.openId", t.getMessage)))
        }
      }
    }

    def apply(f: Either[Form[String],Account] => Request[AnyContent] => Result): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => immediate(t))))
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