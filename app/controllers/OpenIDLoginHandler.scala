package controllers

import _root_.models.sql.OpenIDAssociation
import play.api.libs.openid._
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import concurrent.Future
import play.api.i18n.Messages

/**
 * Default object instantiation
 */
object OpenIDLoginHandler extends OpenIDLoginHandler(play.api.Play.current)

/**
 * OpenID login handler implementation.
 */
class OpenIDLoginHandler(app: play.api.Application) extends base.LoginHandler {

  import models.forms.UserForm
  import models.sql._

  val openidError = """
    |There was an error connecting to your OpenID provider.""".stripMargin

  def login = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.login(form = UserForm.openid, action = routes.OpenIDLoginHandler.loginPost))
  }

  def loginPost = optionalUserAction { implicit maybeUser =>
    implicit request =>
      UserForm.openid.bindFromRequest.fold(
        error => {
          Logger.info("bad request " + error.toString)
          BadRequest(views.html.login(form = error, action = routes.OpenIDLoginHandler.loginPost))
        },
        {
          case (openid) => AsyncResult(
            OpenID.redirectURL(
              openid,
              routes.OpenIDLoginHandler.openIDCallback.absoluteURL(),
              Seq("email" -> "http://schema.openid.net/contact/email",
                "axemail" -> "http://axschema.org/contact/email"))
              .map(url => Redirect(url)))
        })
  }

  def openIDCallback = Action { implicit request =>
    AsyncResult(
      OpenID.verifiedId.map { info =>
        // check if there's a user with the right id
        OpenIDAssociation.findByUrl(info.id) match {
          // FIXME: Handle case where user exists in auth DB but not
          // on the server.	
          case Some(assoc) => gotoLoginSucceeded(assoc.user.get.profile_id)
          case None =>
            val email = extractEmail(info.attributes).getOrElse(sys.error("No openid email"))
            Async {
              rest.AdminDAO(userProfile = None).createNewUserProfile.map {
                case Right(entity) => {
                  models.sql.OpenIDUser.create(email, entity.id).map { user =>
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
      } recoverWith {
        case e: Throwable => Future.successful {
          Redirect(routes.Application.login())
            .flashing("error" -> Messages("openid.openIdError", e.getMessage))
        }
      }
    )
  }

  private def extractEmail(attrs: Map[String, String]): Option[String] = {
    println("ATTRS: " + attrs)
    attrs.get("email").orElse(attrs.get("axemail"))
  }
}