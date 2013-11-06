package controllers.core

import controllers.base.LoginHandler
import models.AccountDAO
import play.api._
import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsString
import com.google.inject._

/**
 * Handler for Mozilla Persona-based login.
 *
 * NOTE: Not tested for some time...
 *
 * @param globalConfig
 */
@Singleton
case class PersonaLoginHandler @Inject()(implicit globalConfig: global.GlobalConfig) extends LoginHandler {

  private lazy val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get

  val PERSONA_URL = "https://verifier.login.persona.org/verify"
  val EHRI_URL = "localhost"; //"http://ehritest.dans.knaw.nl"

  def personaLogin = Action {
    // TODO: Implement a login action for Persona...
    Ok("Mozilla Persona should handle this view...")
  }
  
  def personaLoginPost = Action { implicit request =>
    val assertion: String = request.body.asFormUrlEncoded.map(
      _.getOrElse("assertion", Seq()).headOption.getOrElse("")).getOrElse("")

    val validate = Map("assertion" -> Seq(assertion), "audience" -> Seq(EHRI_URL))

    Async {
      WS.url(PERSONA_URL).post(validate).map { response =>
        response.json \ "status" match {
          case js @ JsString("okay") => {
            val email: String = (response.json \ "email").as[String]

            userDAO.findByEmail(email) match {
              case Some(account) => gotoLoginSucceeded(email)
              case None => {
                Async {
                  rest.AdminDAO().createNewUserProfile.map {
                    case Right(up) => {
                      userDAO.create(up.id, email).map { acc =>
                        gotoLoginSucceeded(acc.id)
                      }.getOrElse(BadRequest("Creation of user db failed!"))
                    }
                    case Left(err) => BadRequest("Unexpected REST error: " + err)
                  }
                }
              }
            }
          }
          case other => BadRequest(other.toString)
        }
      }
    }
  }
}