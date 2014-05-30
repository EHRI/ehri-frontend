package controllers.guides

import controllers.base.AuthController
import play.api.mvc.Controller
import controllers.base.ControllerHelpers

import com.google.inject._
import backend.Backend
import models.AccountDAO
import models.{Guide, GuidePage}


case class GuidePages @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AuthController with ControllerHelpers {

   private val formPage = models.GuidePage.form
   private final val guidePagesRoutes = controllers.guides.routes.GuidePages

   /*
   *	Routes related action
   *
   *	Guides
   */


   /*
   *	Routes related action
   *
   *	Pages
   */

   def edit(gPath: String, path: String) = userProfileAction { implicit userOpt => implicit request =>
     itemOr404 {
       for {
         guide <- Guide.find(gPath)
         page <- guide.getPage(path)
       } yield Ok(views.html.guidePage.edit(guide, page,
         formPage.fill(page), GuidePage.find(gPath), Guide.findAll(),
            guidePagesRoutes.editPost(gPath, path)))
     }
   }

   def editPost(gPath: String, path: String) = userProfileAction { implicit userOpt => implicit request =>
     itemOr404 {
       for {
         guide <- Guide.find(gPath)
         page <- guide.getPage(path)
       } yield {
         formPage.bindFromRequest.fold(
           errorForm => {
             BadRequest(views.html.guidePage.edit(guide, page,
               errorForm, GuidePage.find(gPath),
               Guide.findAll(), guidePagesRoutes.editPost(gPath, path)))
           }, {
              case page: GuidePage =>
                page.update()
                Redirect(controllers.guides.routes.Guides.show(gPath))
                  .flashing("success" -> "item.update.confirmation")
           }
         )
       }
     }
   }

   def create(gPath: String) = userProfileAction { implicit userOpt => implicit request =>
     itemOr404 {
       Guide.find(gPath, activeOnly = false).map { guide =>
         Ok(views.html.guidePage.create(guide,
           formPage.fill(GuidePage.blueprint(guide.objectId)),
           GuidePage.find(gPath), Guide.findAll(),
           guidePagesRoutes.createPost(gPath)))
       }
     }
   }

   def createPost(gPath: String) = userProfileAction { implicit userOpt => implicit request =>
     itemOr404 {
       Guide.find(gPath, activeOnly = false).flatMap { guide =>
         formPage.bindFromRequest.fold(
           errorForm => {
             Some(BadRequest(views.html.guidePage.create(guide, errorForm, GuidePage.find(gPath), Guide.findAll(), guidePagesRoutes.createPost(gPath))))
           }, {
             case GuidePage(_, layout, name, path, menu, cypher, parent) => {
               GuidePage.create(layout, name, path, menu, cypher, guide.objectId).map { guidePage =>
                 Redirect(controllers.guides.routes.Guides.show(guide.path))
                   .flashing("success" -> "item.create.confirmation")
               }
             }
           }
         )
       }
     }
   }


   def deletePost(gPath: String, path: String) = userProfileAction { implicit userOpt => implicit request =>
     println(s"Find $gPath $path")
     itemOr404 {
       println(Guide.find(gPath))

       for {
         guide <- Guide.find(gPath)
         page <- guide.getPage(path)
       } yield {
         page.delete()
         Redirect(controllers.guides.routes.Guides.show(gPath))
       }
     }
   }
 }