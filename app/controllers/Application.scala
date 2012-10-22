package controllers

import play.api._
import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.libs.openid._
import play.api.libs.concurrent.execution.defaultContext
import jp.t2v.lab.play20.auth.Auth
import jp.t2v.lab.play20.auth.LoginLogout
import scala.concurrent.impl.Future
import scala.util.Success
import scala.util.Failure

import play.api.Play.current


object Application extends Controller with Auth with LoginLogout with Authorizer {

  lazy val loginHandler: LoginHandler = current.plugin(classOf[LoginHandler]).get
    
  val openidError = """
    |There was an error connecting to your OpenID provider.""".stripMargin

  val PERSONA_URL = "https://verifier.login.persona.org/verify"
  val EHRI_URL = "localhost"; //"http://ehritest.dans.knaw.nl"

  def index = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.index("Your new application is ready."))
  }

  def test = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.test("Testing login here..."))
  }

  def login = loginHandler.login

  def loginPost = loginHandler.loginPost
  
  def logout = Action { implicit request =>
    gotoLogoutSucceeded
  }

  def authenticate = Action { implicit request =>
    val assertion: String = request.body.asFormUrlEncoded.map(
      _.getOrElse("assertion", Seq()).headOption.getOrElse("")).getOrElse("");

    val validate = Map("assertion" -> Seq(assertion), "audience" -> Seq(EHRI_URL))

    Async {
      WS.url(PERSONA_URL).post(validate).map { response =>
        response.json \ "status" match {
          case js @ JsString("okay") => {
            val email: String = (response.json \ "email").as[String]

            models.sql.PersonaUser.authenticate(email) match {
              case Some(user) => gotoLoginSucceeded(email)
              case None => {
                Async {
                  models.AdminDAO().createNewUserProfile.map {
                    case Right(entity) => {
                      models.sql.PersonaUser.create(entity.property("identifier").map(_.as[String]).get, email).map { user =>
                        gotoLoginSucceeded(user.email)
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