package controllers.guides

import controllers.base.AuthController
import play.api.mvc.Controller
import controllers.base.ControllerHelpers

import com.google.inject._
import backend.Backend
import models.AccountDAO
import models.{Guide, GuidePage}
import play.api.http.HeaderNames


case class GuidePages @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AuthController with ControllerHelpers {

  private val formPage = models.GuidePage.form
  private final val guidePagesRoutes = controllers.guides.routes.GuidePages

  def edit(gPath: String, path: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      for {
        guide <- Guide.find(gPath)
        page <- guide.findPage(path)
      } yield Ok(views.html.guidePage.edit(guide, page,
        formPage.fill(page), GuidePage.find(gPath), Guide.findAll(),
        guidePagesRoutes.editPost(gPath, path)))
    }
  }

  def editPost(gPath: String, path: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      for {
        guide <- Guide.find(gPath)
        page <- guide.findPage(path)
      } yield {
        formPage.bindFromRequest.fold(
          errorForm => {
            BadRequest(views.html.guidePage.edit(guide, page,
              errorForm, guide.findPages(),
              Guide.findAll(), guidePagesRoutes.editPost(gPath, path)))
          },
          updated => {
            updated.copy(id = page.id, parent = guide.id).update()
            Redirect(controllers.guides.routes.Guides.show(gPath))
              .flashing("success" -> "item.update.confirmation")
          }
        )
      }
    }
  }

  def create(gPath: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      Guide.find(gPath, activeOnly = false).map { guide =>
        Ok(views.html.guidePage.create(guide,
          formPage.fill(GuidePage.blueprint(guide.id)),
          GuidePage.find(gPath), Guide.findAll(),
          guidePagesRoutes.createPost(gPath)))
      }
    }
  }

  def createPost(gPath: String) = withUserAction { implicit user => implicit request =>
    itemOr404 {
      Guide.find(gPath, activeOnly = false).flatMap { guide =>
        formPage.bindFromRequest.fold(
        errorForm => {
          Some(BadRequest(views.html.guidePage.create(guide, errorForm,
            guide.findPages(), Guide.findAll(), guidePagesRoutes.createPost(gPath))))
        }, {
          case GuidePage(_, layout, name, path, menu, cypher, parent) => {
            GuidePage.create(layout, name, path, menu, cypher, guide.id).map { guidePage =>
              Redirect(controllers.guides.routes.Guides.show(guide.path))
                .flashing("success" -> "item.create.confirmation")
            }
          }
        }
        )
      }
    }
  }

  def delete(gPath: String, path: String) = withUserAction { implicit user => implicit request =>
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

  def deletePost(gPath: String, path: String) = withUserAction { implicit user => implicit request =>
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