package controllers.guides

import java.sql.SQLException

import auth.AccountManager
import controllers.base.AdminController

import javax.inject._
import backend.Backend
import models.sql.IntegrityError
import models.{GuideDAO, Guide, GuidePage}
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi

import scala.util.{Success, Failure}


@Singleton
case class GuidePages @Inject()(implicit app: play.api.Application, cache: CacheApi, globalConfig: global.GlobalConfig, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup, messagesApi: MessagesApi, guideDAO: GuideDAO) extends AdminController {

  private val formPage = models.GuidePage.form
  private final val guidePagesRoutes = controllers.guides.routes.GuidePages

  def edit(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- guideDAO.find(gPath)
        page <- guideDAO.findPage(guide, path)
      } yield Ok(views.html.guidePage.edit(guide, page,
        formPage.fill(page), guideDAO.findPage(gPath), guideDAO.findAll(),
        guidePagesRoutes.editPost(gPath, path)))
    }
  }

  def editPost(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- guideDAO.find(gPath)
        page <- guideDAO.findPage(guide, path)
      } yield {
        val boundForm = formPage.bindFromRequest
        boundForm.fold(
          errorForm =>
            BadRequest(views.html.guidePage.edit(guide, page,
              errorForm, guideDAO.findPages(guide),
              guideDAO.findAll(), guidePagesRoutes.editPost(gPath, path))),
          updated => {
            guideDAO.updatePage(updated.copy(id = page.id, parent = guide.id)) match {
              case Success(()) => Redirect(controllers.guides.routes.Guides.show(gPath))
                .flashing("success" -> "item.update.confirmation")
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(GuidePage.PATH, "constraints.uniqueness")
                BadRequest(views.html.guidePage.edit(guide, page,
                  errorForm, guideDAO.findPages(guide),
                  guideDAO.findAll(), guidePagesRoutes.editPost(gPath, path)))
              case Failure(e) => throw e
            }
          }
        )
      }
    }
  }

  def create(gPath: String) = WithUserAction { implicit request =>
    itemOr404 {
      try {
        guideDAO.find(gPath, activeOnly = false).map { guide =>
          Ok(views.html.guidePage.create(guide,
            formPage.fill(GuidePage.blueprint(guide.id)),
            guideDAO.findPage(gPath), guideDAO.findAll(),
            guidePagesRoutes.createPost(gPath)))
        }
      } catch {
        case e: SQLException =>
          println(s"Got exception $e")
          throw e
      }
    }
  }

  def createPost(gPath: String) = WithUserAction { implicit request =>
    itemOr404 {
      guideDAO.find(gPath, activeOnly = false).flatMap { guide =>
        val boundForm = formPage.bindFromRequest
        boundForm.fold(
          errorForm => {
            Some(BadRequest(views.html.guidePage.create(guide, errorForm,
              guideDAO.findPages(guide), guideDAO.findAll(), guidePagesRoutes.createPost(gPath))))
          }, {
            case GuidePage(_, layout, name, path, menu, cypher, parent, description, params) =>
              guideDAO.createPage(layout, name, path, menu, cypher, guide.id, description, params) match {
                case Success(idOpt) => idOpt.map { guidePage =>
                  Redirect(controllers.guides.routes.Guides.show(guide.path))
                    .flashing("success" -> "item.create.confirmation")
                }
                case Failure(IntegrityError(e)) =>
                  val errorForm = boundForm.withError(GuidePage.PATH, "constraints.uniqueness")
                  Some(BadRequest(views.html.guidePage.create(guide, errorForm,
                    guideDAO.findPages(guide), guideDAO.findAll(), guidePagesRoutes.createPost(gPath))))
                case Failure(e) => throw e
              }
        }
        )
      }
    }
  }

  def delete(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- guideDAO.find(gPath)
        page <- guideDAO.findPage(guide, path)
      } yield {
        Ok(views.html.guidePage.delete(guide, page, guideDAO.findAll(),
          guideDAO.findPages(guide), guidePagesRoutes.deletePost(gPath, path)))
      }
    }
  }

  def deletePost(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- guideDAO.find(gPath)
        page <- guideDAO.findPage(guide, path)
      } yield {
        guideDAO.deletePage(page)
        Redirect(controllers.guides.routes.Guides.show(gPath))
      }
    }
  }
}