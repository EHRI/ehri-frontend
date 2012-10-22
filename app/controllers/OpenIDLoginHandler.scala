package controllers

import jp.t2v.lab.play20.auth.LoginLogout
import jp.t2v.lab.play20.auth.Auth
import play.api.mvc.Controller
import play.api.libs.openid._
import play.api.libs.concurrent.execution.defaultContext
import play.api._
import play.api.mvc._

object OpenIDLoginHandler extends OpenIDLoginHandler(play.api.Play.current)

class OpenIDLoginHandler(app: play.api.Application) extends LoginHandler with Controller with Auth with LoginLogout with Authorizer {

  import forms.UserForm

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
              routes.OpenIDLoginHandler.openIDCallback.absoluteURL(),
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
}