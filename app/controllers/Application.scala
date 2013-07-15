package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current
import base.{Authorizer,AuthController,LoginHandler}
import play.api.data.Form
import play.api.data.Forms._
import defines.EntityType
import models.json.RestReadable
import models.base.AnyModel


object Application extends Controller with Auth with Authorizer with AuthController {

  /**
   * Look up the 'show' page of a generic item id
   * @param id
   */
  def genericShow(id: String) = Action {
    NotImplemented
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
        implicit val rd: RestReadable[AnyModel] = AnyModel.Converter
        rest.SearchDAO(userOpt).list(List(id)).map { listOrErr =>
          listOrErr.right.map{ list =>
            list match {
              case Nil => NotFound(views.html.errors.itemNotFound())
              case mm :: _ =>
                getUrlForType(mm.isA, mm.id).map(Redirect(_)) getOrElse NotFound(views.html.errors.itemNotFound())
            }
          }
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

  private def getUrlForType(`type`: EntityType.Value, id: String): Option[Call] = {
    `type` match {
      // FIXME: Handle case not given here!
      case EntityType.DocumentaryUnit => Some(routes.DocumentaryUnits.get(id))
      case EntityType.Repository => Some(routes.Repositories.get(id))
      case EntityType.HistoricalAgent => Some(routes.HistoricalAgents.get(id))
      case EntityType.UserProfile => Some(controllers.core.routes.UserProfiles.get(id))
      case EntityType.Link => Some(controllers.core.routes.Links.get(id))
      case EntityType.Annotation => Some(controllers.core.routes.Annotations.get(id))
      case EntityType.Concept => Some(routes.Concepts.get(id))
      case EntityType.Vocabulary => Some(routes.Vocabularies.get(id))
      case _ => None
    }
  }
}