package controllers.guides

import java.sql.SQLException
import javax.inject._

import controllers.AppComponents
import controllers.base.AdminController
import models.sql.IntegrityError
import models.{GuidePage, GuideService}
import play.api.mvc.ControllerComponents

import scala.util.{Failure, Success}


@Singleton
case class GuidePages @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  guides: GuideService
) extends AdminController {

  private val formPage = models.GuidePage.form
  private final val guidePagesRoutes = controllers.guides.routes.GuidePages

  def edit(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- guides.find(gPath)
        page <- guides.findPage(guide, path)
      } yield Ok(views.html.admin.guidePage.edit(guide, page,
        formPage.fill(page), guides.findPage(gPath), guides.findAll(),
        guidePagesRoutes.editPost(gPath, path)))
    }
  }

  def editPost(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- guides.find(gPath)
        page <- guides.findPage(guide, path)
      } yield {
        val boundForm = formPage.bindFromRequest()
        boundForm.fold(
          errorForm =>
            BadRequest(views.html.admin.guidePage.edit(guide, page,
              errorForm, guides.findPages(guide),
              guides.findAll(), guidePagesRoutes.editPost(gPath, path))),
          updated => {
            guides.updatePage(updated.copy(id = page.id, parent = guide.id)) match {
              case Success(()) => Redirect(controllers.guides.routes.Guides.show(gPath))
                .flashing("success" -> "item.update.confirmation")
              case Failure(IntegrityError(e)) =>
                val errorForm = boundForm.withError(GuidePage.PATH, "constraints.uniqueness")
                BadRequest(views.html.admin.guidePage.edit(guide, page,
                  errorForm, guides.findPages(guide),
                  guides.findAll(), guidePagesRoutes.editPost(gPath, path)))
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
        guides.find(gPath, activeOnly = false).map { guide =>
          Ok(views.html.admin.guidePage.create(guide,
            formPage.fill(GuidePage.blueprint(guide.id)),
            guides.findPage(gPath), guides.findAll(),
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
      guides.find(gPath, activeOnly = false).flatMap { guide =>
        val boundForm = formPage.bindFromRequest()
        boundForm.fold(
          errorForm => {
            Some(BadRequest(views.html.admin.guidePage.create(guide, errorForm,
              guides.findPages(guide), guides.findAll(), guidePagesRoutes.createPost(gPath))))
          }, {
            case GuidePage(_, layout, name, path, menu, cypher, parent, description, params) =>
              guides.createPage(layout, name, path, menu, cypher, guide.id, description, params) match {
                case Success(idOpt) => idOpt.map { guidePage =>
                  Redirect(controllers.guides.routes.Guides.show(guide.path))
                    .flashing("success" -> "item.create.confirmation")
                }
                case Failure(IntegrityError(e)) =>
                  val errorForm = boundForm.withError(GuidePage.PATH, "constraints.uniqueness")
                  Some(BadRequest(views.html.admin.guidePage.create(guide, errorForm,
                    guides.findPages(guide), guides.findAll(), guidePagesRoutes.createPost(gPath))))
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
        guide <- guides.find(gPath)
        page <- guides.findPage(guide, path)
      } yield {
        Ok(views.html.admin.guidePage.delete(guide, page, guides.findAll(),
          guides.findPages(guide), guidePagesRoutes.deletePost(gPath, path)))
      }
    }
  }

  def deletePost(gPath: String, path: String) = WithUserAction { implicit request =>
    itemOr404 {
      for {
        guide <- guides.find(gPath)
        page <- guides.findPage(guide, path)
      } yield {
        guides.deletePage(page)
        Redirect(controllers.guides.routes.Guides.show(gPath))
      }
    }
  }
}
