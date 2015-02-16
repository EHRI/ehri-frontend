package controllers.guides

import java.sql.SQLException

import auth.AccountManager
import controllers.base.AdminController

import com.google.inject._
import backend.Backend
import models.sql.IntegrityError
import models.{Guide, GuidePage}

import scala.util.{Success, Failure}


@Singleton
case class GuidePages @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup) extends AdminController {

  private val formPage = models.GuidePage.form
  private final val guidePagesRoutes = controllers.guides.routes.GuidePages

  def edit(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- Guide.find(gPath)
        page <- guide.findPage(path)
      } yield Ok(views.html.guidePage.edit(guide, page,
        formPage.fill(page), GuidePage.find(gPath), Guide.findAll(),
        guidePagesRoutes.editPost(gPath, path)))
    }
  }

  def editPost(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- Guide.find(gPath)
        page <- guide.findPage(path)
      } yield {
        val boundForm = formPage.bindFromRequest
        boundForm.fold(
          errorForm =>
            BadRequest(views.html.guidePage.edit(guide, page,
              errorForm, guide.findPages(),
              Guide.findAll(), guidePagesRoutes.editPost(gPath, path))),
          updated => {
            updated.copy(id = page.id, parent = guide.id).update() match {
              case Success(()) => Redirect(controllers.guides.routes.Guides.show(gPath))
                .flashing("success" -> "item.update.confirmation")
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(GuidePage.PATH, "constraints.uniqueness")
                BadRequest(views.html.guidePage.edit(guide, page,
                  errorForm, guide.findPages(),
                  Guide.findAll(), guidePagesRoutes.editPost(gPath, path)))
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
        Guide.find(gPath, activeOnly = false).map { guide =>
          Ok(views.html.guidePage.create(guide,
            formPage.fill(GuidePage.blueprint(guide.id)),
            GuidePage.find(gPath), Guide.findAll(),
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
      Guide.find(gPath, activeOnly = false).flatMap { guide =>
        val boundForm = formPage.bindFromRequest
        boundForm.fold(
          errorForm => {
            Some(BadRequest(views.html.guidePage.create(guide, errorForm,
              guide.findPages(), Guide.findAll(), guidePagesRoutes.createPost(gPath))))
          }, {
            case GuidePage(_, layout, name, path, menu, cypher, parent, description, params) =>
              GuidePage.create(layout, name, path, menu, cypher, guide.id, description, params) match {
                case Success(idOpt) => idOpt.map { guidePage =>
                  Redirect(controllers.guides.routes.Guides.show(guide.path))
                    .flashing("success" -> "item.create.confirmation")
                }
                case Failure(IntegrityError(e)) =>
                  val errorForm = boundForm.withError(GuidePage.PATH, "constraints.uniqueness")
                  Some(BadRequest(views.html.guidePage.create(guide, errorForm,
                    guide.findPages(), Guide.findAll(), guidePagesRoutes.createPost(gPath))))
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
        guide <- Guide.find(gPath)
        page <- guide.findPage(path)
      } yield {
        Ok(views.html.guidePage.delete(guide, page, Guide.findAll(),
            guide.findPages(), guidePagesRoutes.deletePost(gPath, path)))
      }
    }
  }

  def deletePost(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- Guide.find(gPath)
        page <- guide.findPage(path)
      } yield {
        page.delete()
        Redirect(controllers.guides.routes.Guides.show(gPath))
      }
    }
  }
}