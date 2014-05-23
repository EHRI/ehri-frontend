package controllers.guides

import controllers.base.AuthController
import play.api.mvc.{Action, Controller}
import controllers.base.ControllerHelpers
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits._

import play.api.db._
import play.api.Play.current

import com.google.inject._
import global.GlobalConfig
import backend.Backend
import play.api.Routes
import play.api.http.MimeTypes
import models.AccountDAO
import models.{Guide, GuidesPage}

import views.Helpers

import anorm._
import anorm.SqlParser._

case class GuidesAdmin @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AuthController with ControllerHelpers {

  private val formGuide = models.Guide.form
  private val formPage = models.GuidesPage.form
  private final val guidesRoutes = controllers.guides.routes.GuidesAdmin

  /*
  *	Routes related action
  *
  *	Guides
  */

  /* List the available guides */
  def listGuides() = userProfileAction {
    implicit userOpt => implicit request =>
      Ok(views.html.list(Guide.findAll()))
  }

  def edit(path: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(path) match {
        case Some(guide) => Ok(views.html.edit(formGuide.fill(guide), guide, Guide.findAll(), Some(guide.getPages), guidesRoutes.editPost(path)))
        case _ => Ok(views.html.list(Guide.findAll()))
      }
  }

  def delete(path: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(path) match {
        case Some(guide) => {
          guide.delete() match {
            case 1 => Ok(views.html.list(Guide.findAll()))
            case _ => BadRequest(views.html.list(Guide.findAll()))
          }
        }
        case _ => Ok(views.html.list(Guide.findAll()))
      }
  }

  def editPost(path: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(path) match {
        case Some(guide) => {
          formGuide.bindFromRequest.fold(
          errorForm => {
            BadRequest(views.html.edit(errorForm, guide, Guide.findAll(), Some(guide.getPages), guidesRoutes.editPost(path)))
          }, {
            case g: Guide =>
              g.update()
              Ok(views.html.edit(formGuide.fill(g), guide, Guide.findAll(), Some(guide.getPages), guidesRoutes.editPost(path)))
          }
          )
        }
        case _ => Ok(views.html.list(Guide.findAll()))
      }
  }

  def create() = userProfileAction {
    implicit userOpt => implicit request =>
      Ok(views.html.create(formGuide, Guide.findAll(), guidesRoutes.createPost))
  }

  def createPost() = userProfileAction {
    implicit userOpt => implicit request =>
      formGuide.bindFromRequest.fold(
      errorForm => {
        BadRequest(views.html.create(formGuide, Guide.findAll(), guidesRoutes.createPost))
      }, {
        case Guide(_, name, path, picture, description, active, _) =>
          Guide.create(name, path, picture, description) match {
            case Some(i) => Ok(views.html.list(Guide.findAll()))
            case _ => Ok(views.html.create(formGuide, Guide.findAll(), guidesRoutes.createPost))
          }

      }
      )
  }


  /*
  *	Routes related action
  *
  *	Pages
  */

  def listPages(path: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(path) match {
        case Some(guide) => Ok(views.html.p.list(GuidesPage.findAll(path), guide, Guide.findAll()))
        case _ => Ok(views.html.list(Guide.findAll()))
      }
  }

  def editPages(gPath: String, path: String) = userProfileAction {
    implicit userOpt => implicit request =>

      Guide.find(gPath) match {
        case Some(guide) => {
          guide.getPage(path) match {
            case Some(pageLayout) => Ok(views.html.p.edit(formPage.fill(pageLayout), pageLayout, guide, GuidesPage.findAll(gPath), Guide.findAll(), guidesRoutes.editPagesPost(gPath, path)))
            case _ => BadRequest(views.html.p.list(GuidesPage.findAll(gPath), guide, Guide.findAll()))
          }

        }
        case _ => Ok(views.html.list(Guide.findAll()))
      }


  }

  def editPagesPost(gPath: String, path: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(gPath) match {
        case Some(guide) => {
          guide.getPage(path) match {
            case Some(pageLayout) =>
              formPage.bindFromRequest.fold(
              errorForm => {
                BadRequest(views.html.p.edit(errorForm, pageLayout, guide, GuidesPage.findAll(gPath), Guide.findAll(), guidesRoutes.editPagesPost(gPath, path)))
              }, {
                case page: GuidesPage =>
                  page.update()
                  Ok(views.html.p.list(GuidesPage.findAll(gPath), guide, Guide.findAll()))
              }
              )
            case _ => BadRequest(views.html.p.list(GuidesPage.findAll(gPath), guide, Guide.findAll()))
          }


        }
        case _ => Ok(views.html.list(Guide.findAll()))
      }

  }

  def createPages(gPath: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(gPath) match {
        case Some(guide) => Ok(views.html.p.create(formPage.fill(GuidesPage.blueprint(guide.objectId)), guide, GuidesPage.findAll(gPath), Guide.findAll(), guidesRoutes.createPagesPost(gPath)))
        case _ => Ok(views.html.list(Guide.findAll()))
      }

  }

  def createPagesPost(gPath: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(gPath) match {
        case Some(guide) => {
          formPage.bindFromRequest.fold(
          errorForm => {
            BadRequest(views.html.p.create(errorForm, guide, GuidesPage.findAll(gPath), Guide.findAll(), guidesRoutes.createPagesPost(gPath)))
          }, {
            case GuidesPage(_, layout, name, path, menu, cypher, parent) =>
              GuidesPage.create(layout, name, path, menu, cypher, guide.objectId) match {
                case Some(i) => Ok(views.html.p.list(GuidesPage.findAll(gPath), guide, Guide.findAll()))
                case _ => BadRequest(views.html.p.create(formPage.fill(GuidesPage.blueprint(guide.objectId)), guide, GuidesPage.findAll(gPath), Guide.findAll(), guidesRoutes.createPagesPost(gPath)))
              }

          }
          )


        }
        case _ => Ok(views.html.list(Guide.findAll()))
      }

  }


  def deletePages(gPath: String, path: String) = userProfileAction {
    implicit userOpt => implicit request =>
      Guide.find(gPath) match {
        case Some(guide) => {
          guide.getPage(path) match {
            case Some(page) => {
              page.delete() match {
                case 1 => Ok(views.html.p.list(GuidesPage.findAll(gPath), guide, Guide.findAll()))
                case _ => BadRequest(views.html.p.list(GuidesPage.findAll(gPath), guide, Guide.findAll()))
              }
            }
            case _ => BadRequest(views.html.p.list(GuidesPage.findAll(gPath), guide, Guide.findAll()))
          }
        }
        case _ => Ok(views.html.list(Guide.findAll()))
      }
  }
}