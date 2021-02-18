package controllers.guides

import javax.inject._

import controllers.AppComponents
import controllers.base.AdminController
import models.sql.IntegrityError
import models.{Guide, GuideService}
import play.api.mvc.ControllerComponents

import scala.util.{Failure, Success}


@Singleton
case class Guides @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  guides: GuideService
) extends AdminController {

  private val formGuide = models.Guide.form
  private final val guidesRoutes = controllers.guides.routes.Guides

  def list() = OptionalUserAction { implicit request =>
    Ok(views.html.admin.guide.list(guides.findAll()))
  }

  def show(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guides.find(path, activeOnly = false).map { guide =>
        Ok(views.html.admin.guide.show(guide, guides.findPages(guide), guides.findAll()))
      }
    }
  }

  def edit(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guides.find(path, activeOnly = false).map { guide =>
        Ok(views.html.admin.guide.edit(guide, formGuide.fill(guide), guides.findAll(),
          guides.findPages(guide), guidesRoutes.editPost(path)))
      }
    }
  }

  def editPost(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guides.find(path, activeOnly = false).map { guide =>
        val boundForm = formGuide.bindFromRequest()
        boundForm.fold(
          errorForm => {
            BadRequest(views.html.admin.guide.edit(guide, errorForm, guides.findAll(),
              guides.findPages(guide), guidesRoutes.editPost(path)))
          },
          updated => {
            // This ensures we don't depend on the objectId in the form,
            // which might differ from that in the form if someone
            // has somehow changed it...
            guides.update(updated.copy(id = guide.id)) match {
              case Success(_) => Redirect(guidesRoutes.show(updated.path))
                .flashing("success" -> "item.update.confirmation")
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(Guide.PATH, "constraints.uniqueness")
                BadRequest(views.html.admin.guide.edit(guide, errorForm, guides.findAll(),
                  guides.findPages(guide), guidesRoutes.editPost(path)))
              case Failure(e) => throw e
            }
          }
        )
      }
    }
  }

  def create() = WithUserAction { implicit request =>
    Ok(views.html.admin.guide.create(formGuide.fill(Guide.blueprint()), guides.findAll(), guidesRoutes.createPost()))
  }

  def createPost() = WithUserAction { implicit request =>
    val boundForm = formGuide.bindFromRequest()
    boundForm.fold(
      errorForm => {
        BadRequest(views.html.admin.guide.create(errorForm, guides.findAll(), guidesRoutes.createPost()))
      }, {
        case Guide(_, name, path, picture, virtualUnit, description, css, active, default) =>
          itemOr404 {
            guides.create(name, path, picture, virtualUnit, description, css = css , active = active) match {
              case Success(guideOpt) => guideOpt.map { guide =>
                Redirect(guidesRoutes.show(guide.path))
              }
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(Guide.PATH, "constraints.uniqueness")
                Some(BadRequest(views.html.admin.guide.create(errorForm, guides.findAll(), guidesRoutes.createPost())))
              case Failure(e) => throw e
            }
          }
      }
    )
  }

  def delete(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guides.find(path).map { guide =>
        Ok(views.html.admin.guide.delete(guide, guides.findAll(), guidesRoutes.deletePost(path)))
      }
    }
  }

  def deletePost(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guides.find(path, activeOnly = false).map { guide =>
        guides.delete(guide)
        Redirect(guidesRoutes.list()).flashing("success" -> "item.delete.confirmation")
      }
    }
  }
}
