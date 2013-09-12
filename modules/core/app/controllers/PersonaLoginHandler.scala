package controllers.core

import _root_.controllers.base.LoginHandler
import play.api._
import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsString


/**
 * Handler for Mozilla Persona-based login.
 *
 * NOTE: Not tested for some time...
 *
 * @param globalConfig
 */
case class PersonaLoginHandler(implicit globalConfig: global.GlobalConfig) extends LoginHandler {

  val PERSONA_URL = "https://verifier.login.persona.org/verify"
  val EHRI_URL = "localhost"; //"http://ehritest.dans.knaw.nl"

  def login = Action {
    // TODO: Implement a login action for Persona...
    Ok("Mozilla Persona should handle this view...")
  }
  
  def loginPost = Action { implicit request =>
    val assertion: String = request.body.asFormUrlEncoded.map(
      _.getOrElse("assertion", Seq()).headOption.getOrElse("")).getOrElse("")

    val validate = Map("assertion" -> Seq(assertion), "audience" -> Seq(EHRI_URL))

    Async {
      WS.url(PERSONA_URL).post(validate).map { response =>
        response.json \ "status" match {
          case js @ JsString("okay") => {
            val email: String = (response.json \ "email").as[String]

            models.sql.PersonaAccount.findByEmail(email) match {
              case Some(user) => gotoLoginSucceeded(email)
              case None => {
                Async {
                  rest.AdminDAO(userProfile = None).createNewUserProfile.map {
                    case Right(up) => {
                      models.sql.PersonaAccount.create(up.model.identifier, email).map { user =>
                        gotoLoginSucceeded(user.profile_id)
                      }.getOrElse(BadRequest("Creation of user db failed!"))
                    }
                    case Left(err) => {
                      BadRequest("Unexpected REST error: " + err)
                    }
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