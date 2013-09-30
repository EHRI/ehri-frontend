package controllers.core

import _root_.models.AccountDAO
import controllers.base.LoginHandler
import forms.OpenIDForm
import models.sql.OpenIDAssociation
import play.api.libs.openid._
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import concurrent.Future
import play.api.i18n.Messages
import com.google.inject._

/**
 * OpenID login handler implementation.
 */
@Singleton
case class OpenIDLoginHandler @Inject()(implicit globalConfig: global.GlobalConfig) extends LoginHandler {

  import models.sql._

  private lazy val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get

  val openidError = """
    |There was an error connecting to your OpenID provider.""".stripMargin

  def openIDLogin = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.openIDLogin(OpenIDForm.openid, action = routes.OpenIDLoginHandler.openIDLoginPost))
  }

  def openIDLoginPost = optionalUserAction { implicit maybeUser =>
    implicit request =>
      OpenIDForm.openid.bindFromRequest.fold(
        error => {
          Logger.info("bad request " + error.toString)
          BadRequest(views.html.openIDLogin(error, action = routes.OpenIDLoginHandler.openIDLoginPost))
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
          case Some(assoc) => gotoLoginSucceeded(assoc.user.get.id)
          case None => {
            val email = extractEmail(info.attributes).getOrElse(sys.error("No openid email"))
            userDAO.findByEmail(email).map { acc =>
              OpenIDAssociation.addAssociation(acc, info.id)
              gotoLoginSucceeded(acc.id)
                .withSession("access_uri" -> globalConfig.routeRegistry.default.url)
            } getOrElse {
              Async {
                rest.AdminDAO(userProfile = None).createNewUserProfile.map { 
                  case Right(entity) => {
                    userDAO.create(entity.id, email.toLowerCase).map { account =>
                      OpenIDAssociation.addAssociation(account, info.id)
                      // TODO: Redirect to profile?
                      gotoLoginSucceeded(account.id)
                        .withSession("access_uri" -> globalConfig.routeRegistry.default.url)
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
      } recoverWith {
        case e: Throwable => Future.successful {
          Redirect(controllers.core.routes.OpenIDLoginHandler.openIDLogin)
            .flashing("error" -> Messages("openid.openIdError", e.getMessage))
        }
      }
    )
  }

  /**
   * Pick up the email from OpenID info. This may be stored in different
   * attributes depending on the provider.
   * @param attrs
   * @return
   */
  private def extractEmail(attrs: Map[String, String]): Option[String]
      = attrs.get("email").orElse(attrs.get("axemail"))

}