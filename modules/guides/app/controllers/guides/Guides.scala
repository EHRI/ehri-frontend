package controllers.guides

import auth.AccountManager
import controllers.base.AdminController

import com.google.inject._
import backend.Backend
import models.sql.IntegrityError
import models.{GuidePage, Guide}

import scala.util.{Failure, Success}


@Singleton
case class Guides @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup) extends AdminController {

  private val formGuide = models.Guide.form
  private final val guidesRoutes = controllers.guides.routes.Guides

  def list() = OptionalUserAction { implicit request =>
    Ok(views.html.guide.list(Guide.findAll()))
  }

  def show(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      Guide.find(path, activeOnly = false).map { guide =>
        Ok(views.html.guide.show(guide, guide.findPages(), Guide.findAll()))
      }
    }
  }

  def edit(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      Guide.find(path, activeOnly = false).map { guide =>
        Ok(views.html.guide.edit(guide, formGuide.fill(guide), Guide.findAll(),
          guide.findPages(), guidesRoutes.editPost(path)))
      }
    }
  }

  def editPost(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      Guide.find(path, activeOnly = false).map { guide =>
        val boundForm = formGuide.bindFromRequest
        boundForm.fold(
          errorForm => {
            BadRequest(views.html.guide.edit(guide, errorForm, Guide.findAll(),
              guide.findPages(), guidesRoutes.editPost(path)))
          },
          updated => {
            // This ensures we don't depend on the objectId in the form,
            // which might differ from that in the form if someone
            // has somehow changed it...
            updated.copy(id = guide.id).update() match {
              case Success(_) => Redirect(guidesRoutes.show(updated.path))
                .flashing("success" -> "item.update.confirmation")
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(Guide.PATH, "constraints.uniqueness")
                BadRequest(views.html.guide.edit(guide, errorForm, Guide.findAll(),
                  guide.findPages(), guidesRoutes.editPost(path)))
              case Failure(e) => throw e
            }
          }
        )
      }
    }
  }

  def create() = WithUserAction { implicit request =>
    Ok(views.html.guide.create(formGuide.fill(Guide.blueprint()), Guide.findAll(), guidesRoutes.createPost()))
  }

  def createPost() = WithUserAction { implicit request =>
    val boundForm = formGuide.bindFromRequest
    boundForm.fold(
      errorForm => {
        BadRequest(views.html.guide.create(errorForm, Guide.findAll(), guidesRoutes.createPost()))
      }, {
        case Guide(_, name, path, picture, virtualUnit, description, css, active, default) =>
          itemOr404 {
            Guide.create(name, path, picture, virtualUnit, description, css = css , active = active) match {
              case Success(guideOpt) => guideOpt.map { guide =>
                Redirect(guidesRoutes.show(guide.path))
              }
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(Guide.PATH, "constraints.uniqueness")
                Some(BadRequest(views.html.guide.create(errorForm, Guide.findAll(), guidesRoutes.createPost())))
              case Failure(e) => throw e
            }
          }
      }
    )
  }

  def delete(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      Guide.find(path).map { guide =>
        Ok(views.html.guide.delete(guide, Guide.findAll(), guidesRoutes.deletePost(path)))
      }
    }
  }

  def deletePost(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      Guide.find(path, activeOnly = false).map { guide =>
        guide.delete()
        Redirect(guidesRoutes.list()).flashing("success" -> "item.delete.confirmation")
      }
    }
  }
}