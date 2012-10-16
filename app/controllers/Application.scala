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

object Application extends Controller with Auth with LoginLogout with Authorizer {

  import forms.UserForm

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

  def login = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.login(form = UserForm.openid, action = routes.Application.loginPost))
  }

  def loginPost = optionalUserAction { implicit maybeUser =>
    implicit request =>
      UserForm.openid.bindFromRequest.fold(
        error => {
          Logger.info("bad request " + error.toString)
          BadRequest(views.html.login(form = error, action = routes.Application.loginPost))
        },
        {
          case (openid) => AsyncResult(
            OpenID.redirectURL(
              openid,
              routes.Application.openIDCallback.absoluteURL(),
              Seq("email" -> "http://schema.openid.net/contact/email"))
              .map(url => Redirect(url)))
        })
  }

  def openIDCallback = Action { implicit request =>
    import models.sql.OpenIDAssociation
    AsyncResult(
      OpenID.verifiedId.map { info =>
        // check if there's a user with the right id
        OpenIDAssociation.findByUrl(info.id) match {
          case Some(assoc) => gotoLoginSucceeded(assoc.user.get.profile_id)
          case None =>
            Async {
              models.AdminDAO().createNewUserProfile.map {
                case Right(entity) => {
                  val email = info.attributes.getOrElse("email", sys.error("No openid email"))
                  models.sql.OpenIDUser.create(email, entity.property("identifier").map(_.as[String]).get).map { user =>
                    user.addAssociation(info.id)
                    gotoLoginSucceeded(user.profile_id)
                  }.getOrElse(BadRequest("Creation of user db failed!"))
                }
                case Left(err) => {
                  BadRequest("Unexpected REST error: " + err)
                }
              }
            }
          // TODO: Check error condition?
        }
      })
  }

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