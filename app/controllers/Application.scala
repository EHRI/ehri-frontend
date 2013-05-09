package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current
import base.{Authorizer,AuthController,LoginHandler}
import play.api.data.Form
import play.api.data.Forms._
import models.Entity
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
            case Entity(_, etype, _, _) :: _ => {
              etype match {
                // FIXME: Handle case not given here!
                case EntityType.DocumentaryUnit => Redirect(routes.DocumentaryUnits.get(id))
                case EntityType.Repository => Redirect(routes.Repositories.get(id))
                case EntityType.HistoricalAgent => Redirect(routes.HistoricalAgents.get(id))
                case EntityType.UserProfile => Redirect(routes.UserProfiles.get(id))
                case EntityType.Link => Redirect(routes.Links.get(id))
                case EntityType.Annotation => Redirect(routes.Annotations.get(id))
                case EntityType.Concept => Redirect(routes.Concepts.get(id))
                case EntityType.Vocabulary => Redirect(routes.Vocabularies.get(id))
                case _ => NotFound(views.html.errors.itemNotFound())
              }
            }
          })
        }
      }
    }
  }

  val emailForm = Form(single("email" -> email))

  def login = loginHandler.login
  def loginPost = loginHandler.loginPost
  def logout = loginHandler.logout
}