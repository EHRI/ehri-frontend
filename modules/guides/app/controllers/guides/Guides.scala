package controllers.guides

import controllers.base.AuthController
import play.api.mvc.Controller
import controllers.base.ControllerHelpers

import com.google.inject._
import backend.Backend
import models.{GuidePage, AccountDAO, Guide}


case class Guides @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AuthController with ControllerHelpers {

  private val formGuide = models.Guide.form
  private final val guidesRoutes = controllers.guides.routes.Guides

  def list() = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.guide.list(Guide.findAll()))
  }

  def show(path: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      Guide.find(path, activeOnly = false).map { guide =>
        Ok(views.html.guide.show(guide, guide.findPages(), Guide.findAll()))
      }
    }
  }

  def edit(path: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      Guide.find(path, activeOnly = false).map { guide =>
        Ok(views.html.guide.edit(guide, formGuide.fill(guide), Guide.findAll(),
          guide.findPages(), guidesRoutes.editPost(path)))
      }
    }
  }

  def editPost(path: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      Guide.find(path, activeOnly = false).map { guide =>
        formGuide.bindFromRequest.fold(
          errorForm => {
            BadRequest(views.html.guide.edit(guide, errorForm, Guide.findAll(),
              guide.findPages(), guidesRoutes.editPost(path)))
          },
          updated => {
            // This ensures we don't depend on the objectId in the form,
            // which might differ from that in the form if someone
            // has somehow changed it...
            updated.copy(id = guide.id).update()
            Redirect(guidesRoutes.show(updated.path))
              .flashing("success" -> "item.update.confirmation")
          }
        )
      }
    }
  }

  def create() = withUserAction { implicit user => implicit request =>
    Ok(views.html.guide.create(formGuide.fill(Guide.blueprint()), Guide.findAll(), guidesRoutes.createPost()))
  }

  def createPost() = withUserAction { implicit user => implicit request =>
    formGuide.bindFromRequest.fold(
      errorForm => {
        BadRequest(views.html.guide.create(errorForm, Guide.findAll(), guidesRoutes.createPost()))
      }, {
        case Guide(_, name, path, picture, virtualUnit, description, active, default) => {
          itemOr404 {
            Guide.create(name, path, picture, virtualUnit, description, active = active).map { guide =>
              Redirect(guidesRoutes.show(guide.path))
            }
          }
        }
      }
    )
  }

  def delete(path: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      Guide.find(path).map { guide =>
        Ok(views.html.guide.delete(guide, Guide.findAll(), guidesRoutes.deletePost(path)))
      }
    }
  }

  def deletePost(path: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      Guide.find(path, activeOnly = false).map { guide =>
        guide.delete()
        Redirect(guidesRoutes.list()).flashing("success" -> "item.delete.confirmation")
      }
    }
  }
}