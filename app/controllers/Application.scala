package controllers

import play.api._
import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.libs.concurrent.execution.defaultContext
import jp.t2v.lab.play20.auth.Auth
import jp.t2v.lab.play20.auth.LoginLogout
import scala.concurrent.impl.Future

object Application extends Controller with Auth with LoginLogout with Authorizer {

  val PERSONA_URL = "https://verifier.login.persona.org/verify"
  val EHRI_URL = "localhost"; //"http://ehritest.dans.knaw.nl"

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def test = optionalUserAction { implicit maybeUser =>
    implicit request =>
      println("rendering test...")
      Ok(views.html.test("Testing login here..."))
  }

  def signup = TODO

  def login = Action {
    Ok("This is the login page...")
  }

  def loginPost = TODO

  def logout = Action { implicit request =>
    println("log out...")
    gotoLogoutSucceeded
  }

  val userData = Map("isA" -> "userProfile", "identifier" -> "user0193948", "name" -> "user0193948")

  def authenticate = Action { implicit request =>

    val assertion: String = request.body.asFormUrlEncoded.map(
    		_.getOrElse("assertion", Seq()).headOption.getOrElse("")).getOrElse("");
    //val validate = "assertion=%s&audience=%s".format(assertion, EHRI_URL)
    val validate = Map("assertion" -> Seq(assertion), "audience" -> Seq(EHRI_URL))
    println("Validating with: " + validate)
    Async {
      WS.url(PERSONA_URL).post(validate).map { response =>
        println("persona response: " + response.body)
        response.json \ "status" match {
          case js @ JsString("okay") => {
            println("OKAY!")
            val email: String = (response.json \ "email").as[String]

            models.sql.User.authenticate(email) match {
              case Some(user) => gotoLoginSucceeded(email)
              case None => {
                Async {
                  models.EntityDAO("userProfile").create(userData).map { e =>
                    e match {
                      case Right(entity) => {
                        models.sql.User.create(entity.id, email).map { user =>
                          println("LOGGED IN OKAY!!!")
                          gotoLoginSucceeded(user.email)
                        }.getOrElse(BadRequest("Creation of user db failed!"))
                      }
                      case Left(err) => BadRequest("Unexpected REST error: " + err)
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