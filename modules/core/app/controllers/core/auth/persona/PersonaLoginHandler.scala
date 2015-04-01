package controllers.core.auth.persona

import models.{UserProfile, Account}
import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsString
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.{AnonymousUser, ApiUser, Backend}
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

  import play.api.Play.current

  def globalConfig: global.GlobalConfig
  def backend: Backend
  def accounts: auth.AccountManager

  val PERSONA_URL = "https://verifier.login.persona.org/verify"
  val EHRI_URL = "localhost"; //"http://ehritest.dans.knaw.nl"


  object personaLoginPost {
    def async(f: Either[String,Account] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      val canMessageUsers = play.api.Play.current.configuration
        .getBoolean("ehri.users.messaging.default").getOrElse(false)

      Action.async { implicit request =>
        val assertion: String = request.body.asFormUrlEncoded.map(
          _.getOrElse("assertion", Seq()).headOption.getOrElse("")).getOrElse("")

        val validate = Map("assertion" -> Seq(assertion), "audience" -> Seq(EHRI_URL))

        WS.url(PERSONA_URL).post(validate).flatMap { response =>
          response.json \ "status" match {
            case js @ JsString("okay") =>
              val email: String = (response.json \ "email").as[String]

              accounts.findByEmail(email.toLowerCase).flatMap {
                case Some(account) => f(Right(account))(request)
                case None =>
                  implicit val apiUser = AnonymousUser
                  for {
                    up <- backend.forUser(AnonymousUser).createNewUserProfile[UserProfile](groups = defaultPortalGroups)
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