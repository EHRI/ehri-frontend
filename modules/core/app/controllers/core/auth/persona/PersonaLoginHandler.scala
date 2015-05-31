package controllers.core.auth.persona

import models.{UserProfile, Account}
import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsDefined,JsString}
import javax.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.{AnonymousUser, Backend}
import scala.concurrent.Future
import controllers.core.auth.AccountHelpers

/**
 * Handler for Mozilla Persona-based login.
 *
 * NOTE: Not tested for some time...
 */
@Singleton
trait PersonaLoginHandler extends AccountHelpers {

  self: Controller =>

  def globalConfig: global.GlobalConfig
  def backend: Backend
  def accounts: auth.AccountManager
  implicit def app: play.api.Application

  val PERSONA_URL = "https://verifier.login.persona.org/verify"
  val EHRI_URL = "localhost"; //"http://ehritest.dans.knaw.nl"


  object personaLoginPost {
    def async(f: Either[String,Account] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      val canMessageUsers = app.configuration
        .getBoolean("ehri.users.messaging.default").getOrElse(false)

      Action.async { implicit request =>
        val assertion: String = request.body.asFormUrlEncoded.map(
          _.getOrElse("assertion", Seq()).headOption.getOrElse("")).getOrElse("")

        val validate = Map("assertion" -> Seq(assertion), "audience" -> Seq(EHRI_URL))

        WS.url(PERSONA_URL).post(validate).flatMap { response =>
          response.json \ "status" match {
            case js @ JsDefined(JsString("okay")) =>
              val email: String = (response.json \ "email").as[String]

              accounts.findByEmail(email.toLowerCase).flatMap {
                case Some(account) => f(Right(account))(request)
                case None =>
                  for {
                    up <- backend.withContext(AnonymousUser).createNewUserProfile[UserProfile](groups = defaultPortalGroups)
                    account <- accounts.create(Account(
                      id = up.id,
                      email = email.toLowerCase,
                      verified = true,
                      staff = false,
                      active = true,
                      allowMessaging = canMessageUsers
                    ))
                    r <- f(Right(account))(request)
                  } yield r
              }
            case other => f(Left(other.toString()))(request)
          }
        }
      }
    }

    def apply(f: Either[String,Account] => Request[AnyContent] => Result): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }
}