package controllers.guides

import controllers.base.AuthController
import play.api.mvc.Controller
import controllers.base.ControllerHelpers

import com.google.inject._
import backend.Backend
import models.{GuidePage, AccountDAO, Guide}


case class Guides @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AuthController with ControllerHelpers {

  private val formGuide = models.Guide.form
  private val formPage = models.GuidePage.form
  private final val guidesRoutes = controllers.guides.routes.Guides

  /*
  *	Routes related action
  *
  *	Guides
  */

  /* List the available guides */
  def list() = userProfileAction {
    implicit userOpt => implicit request =>
      Ok(views.html.guide.list(Guide.findAll()))
  }


  def show(path: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(path, active = false) match {
        case Some(guide) => Ok(views.html.guide.show(guide, guide.getPages, Guide.findAll()))
        case _ => Ok(views.html.guide.list(Guide.findAll()))
      }
  }

  def edit(path: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(path, active = false) match {
        case Some(guide) => Ok(views.html.guide.edit(guide, formGuide.fill(guide), Guide.findAll(), Some(guide.getPages), guidesRoutes.editPost(path)))
        case _ => Ok(views.html.guide.list(Guide.findAll()))
      }
  }

  def delete(path: String) = userProfileAction { implicit userOpt => implicit request =>
    itemOr404 {
      Guide.find(path, active = false).map { guide =>
        guide.delete()
        Redirect(guidesRoutes.list()).flashing("success" -> "item.delete.confirmation")
      }
    }
  }

  def editPost(path: String) = userProfileAction { implicit userOpt => implicit request =>
    itemOr404 {
      Guide.find(path, active = false).map { guide =>
        formGuide.bindFromRequest.fold(
          errorForm => {
            BadRequest(views.html.guide.edit(guide, errorForm, Guide.findAll(), Some(guide.getPages), guidesRoutes.editPost(path)))
          },
          guide => {
            guide.update()
            Redirect(guidesRoutes.show(path))
              .flashing("success" -> "item.update.confirmation")
          }
        )
      }
    }
  }

  def create() = userProfileAction {
    implicit userOpt => implicit request =>
      Ok(views.html.guide.create(formGuide.fill(Guide.blueprint()), Guide.findAll(), guidesRoutes.createPost()))
  }

  def createPost() = userProfileAction { implicit userOpt => implicit request =>
    formGuide.bindFromRequest.fold(
      errorForm => {
        BadRequest(views.html.guide.create(errorForm, Guide.findAll(), guidesRoutes.createPost()))
      }, {
        case Guide(_, name, path, picture, description, active, default) => {
          itemOr404 {
            Guide.create(name, path, picture, description).map { guide =>
              Redirect(guidesRoutes.show(guide.path))
            }
          }
        }
      }
    )
  }
}