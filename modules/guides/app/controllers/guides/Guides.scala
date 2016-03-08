package controllers.guides

import auth.AccountManager
import controllers.base.AdminController

import javax.inject._
import backend.DataApi
import models.sql.IntegrityError
import models.{GuideDAO, GuidePage, Guide}
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.MovedPageLookup
import views.MarkdownRenderer

import scala.util.{Failure, Success}


@Singleton
case class Guides @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  guideDAO: GuideDAO
) extends AdminController {

  private val formGuide = models.Guide.form
  private final val guidesRoutes = controllers.guides.routes.Guides

  def list() = OptionalUserAction { implicit request =>
    Ok(views.html.admin.guide.list(guideDAO.findAll()))
  }

  def show(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guideDAO.find(path, activeOnly = false).map { guide =>
        Ok(views.html.admin.guide.show(guide, guideDAO.findPages(guide), guideDAO.findAll()))
      }
    }
  }

  def edit(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guideDAO.find(path, activeOnly = false).map { guide =>
        Ok(views.html.admin.guide.edit(guide, formGuide.fill(guide), guideDAO.findAll(),
          guideDAO.findPages(guide), guidesRoutes.editPost(path)))
      }
    }
  }

  def editPost(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guideDAO.find(path, activeOnly = false).map { guide =>
        val boundForm = formGuide.bindFromRequest
        boundForm.fold(
          errorForm => {
            BadRequest(views.html.admin.guide.edit(guide, errorForm, guideDAO.findAll(),
              guideDAO.findPages(guide), guidesRoutes.editPost(path)))
          },
          updated => {
            // This ensures we don't depend on the objectId in the form,
            // which might differ from that in the form if someone
            // has somehow changed it...
            guideDAO.update(updated.copy(id = guide.id)) match {
              case Success(_) => Redirect(guidesRoutes.show(updated.path))
                .flashing("success" -> "item.update.confirmation")
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(Guide.PATH, "constraints.uniqueness")
                BadRequest(views.html.admin.guide.edit(guide, errorForm, guideDAO.findAll(),
                  guideDAO.findPages(guide), guidesRoutes.editPost(path)))
              case Failure(e) => throw e
            }
          }
        )
      }
    }
  }

  def create() = WithUserAction { implicit request =>
    Ok(views.html.admin.guide.create(formGuide.fill(Guide.blueprint()), guideDAO.findAll(), guidesRoutes.createPost()))
  }

  def createPost() = WithUserAction { implicit request =>
    val boundForm = formGuide.bindFromRequest
    boundForm.fold(
      errorForm => {
        BadRequest(views.html.admin.guide.create(errorForm, guideDAO.findAll(), guidesRoutes.createPost()))
      }, {
        case Guide(_, name, path, picture, virtualUnit, description, css, active, default) =>
          itemOr404 {
            guideDAO.create(name, path, picture, virtualUnit, description, css = css , active = active) match {
              case Success(guideOpt) => guideOpt.map { guide =>
                Redirect(guidesRoutes.show(guide.path))
              }
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(Guide.PATH, "constraints.uniqueness")
                Some(BadRequest(views.html.admin.guide.create(errorForm, guideDAO.findAll(), guidesRoutes.createPost())))
              case Failure(e) => throw e
            }
          }
      }
    )
  }

  def delete(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guideDAO.find(path).map { guide =>
        Ok(views.html.admin.guide.delete(guide, guideDAO.findAll(), guidesRoutes.deletePost(path)))
      }
    }
  }

  def deletePost(path: String) = WithUserAction { implicit request =>
    itemOr404 {
      guideDAO.find(path, activeOnly = false).map { guide =>
        guideDAO.delete(guide)
        Redirect(guidesRoutes.list()).flashing("success" -> "item.delete.confirmation")
      }
    }
  }
}