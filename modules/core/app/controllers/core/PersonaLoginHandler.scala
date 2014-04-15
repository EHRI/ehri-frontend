package controllers.core

import models.{Account, AccountDAO}
import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsString
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.{ApiUser, Backend}
import scala.concurrent.Future

/**
 * Handler for Mozilla Persona-based login.
 *
 * NOTE: Not tested for some time...
 */
@Singleton
trait PersonaLoginHandler {

  self: Controller =>

  import play.api.Play.current

  def globalConfig: global.GlobalConfig
  def backend: Backend
  def userDAO: AccountDAO

  val PERSONA_URL = "https://verifier.login.persona.org/verify"
  val EHRI_URL = "localhost"; //"http://ehritest.dans.knaw.nl"


  object personaLoginPost {
    def async(f: Either[String,Account] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      Action.async { implicit request =>
        val assertion: String = request.body.asFormUrlEncoded.map(
          _.getOrElse("assertion", Seq()).headOption.getOrElse("")).getOrElse("")

        val validate = Map("assertion" -> Seq(assertion), "audience" -> Seq(EHRI_URL))

        WS.url(PERSONA_URL).post(validate).flatMap { response =>
          response.json \ "status" match {
            case js @ JsString("okay") => {
              val email: String = (response.json \ "email").as[String]

              userDAO.findByEmail(email) match {
                case Some(account) => f(Right(account))(request)
                case None => {
                  implicit val apiUser = ApiUser()
                  backend.createNewUserProfile().flatMap { up =>
                    val account = userDAO.create(up.id, email, verified = true, staff = false)
                    f(Right(account))(request)
                  }
                }
              }
            }
            case other => f(Left(other.toString))(request)
          }
        }
      }
    }

    def apply(f: Either[String,Account] => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }
}