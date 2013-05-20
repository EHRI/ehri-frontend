package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current
import base.{Authorizer,AuthController,LoginHandler}
import play.api.data.Form
import play.api.data.Forms._
import models.{UserProfile, Entity}
import defines.EntityType


object Application extends Controller with Auth with LoginLogout with Authorizer with AuthController {

  lazy val loginHandler: LoginHandler = current.plugin(classOf[LoginHandler]).get

  /**
   * Look up the 'show' page of a generic item id
   * @param id
   */
  def genericShow(id: String) = Action {
    NotImplemented
  }


  def index = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      Ok(views.html.index("Your new application is ready."))
    }
  }

  /**
   * Action for redirecting to any item page, given a raw id.
   * TODO: Ultimately implement this in a better way, not
   * requiring two DB hits (including the redirect...)
   * @param id
   * @return
   */
  def get(id: String) = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      AsyncRest {
        rest.SearchDAO(userOpt).list(List(id)).map { listOrErr =>
          listOrErr.right.map(_ match {
            case Nil => NotFound(views.html.errors.itemNotFound())
            case Entity(_, etype, _, _) :: _ =>
              getUrlForType(etype, id).map(Redirect(_)) getOrElse NotFound(views.html.errors.itemNotFound())
          })
        }
      }
    }
  }

  /**
   * Action for redirecting to any item page, given a raw id.
   * TODO: Ultimately implement this in a better way, not
   * requiring two DB hits (including the redirect...)
   * @param id
   * @return
   */
  def getType(`type`: String, id: String) = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      getUrlForType(EntityType.withName(`type`), id).map(Redirect(_)) getOrElse NotFound(views.html.errors.itemNotFound())
    }
  }

  val emailForm = Form(single("email" -> email))

  def login = loginHandler.login
  def loginPost = loginHandler.loginPost
  def logout = loginHandler.logout

  private def getUrlForType(`type`: EntityType.Value, id: String): Option[Call] = {
    `type` match {
      // FIXME: Handle case not given here!
      case EntityType.DocumentaryUnit => Some(routes.DocumentaryUnits.get(id))
      case EntityType.Repository => Some(routes.Repositories.get(id))
      case EntityType.HistoricalAgent => Some(routes.HistoricalAgents.get(id))
      case EntityType.UserProfile => Some(routes.UserProfiles.get(id))
      case EntityType.Link => Some(routes.Links.get(id))
      case EntityType.Annotation => Some(routes.Annotations.get(id))
      case EntityType.Concept => Some(routes.Concepts.get(id))
      case EntityType.Vocabulary => Some(routes.Vocabularies.get(id))
      case _ => None
    }
  }

  /**
    Endpoint for experimental Angular-JS stuff...
   */
  def portal = userProfileAction { implicit userProfile => implicit request =>
    Ok(views.html.portal())
  }
}