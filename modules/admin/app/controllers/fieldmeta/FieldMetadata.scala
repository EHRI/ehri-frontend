package controllers.fieldmeta

import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import play.api.http.MimeTypes
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter

import javax.inject._



@Singleton
case class FieldMetadata @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents
) extends AdminController with ApiBodyParsers {

//  def jsRoutes(): Action[AnyContent] = Action.apply { implicit request =>
//    Ok(
//      JavaScriptReverseRouter("fieldMetadataApi")(
//        controllers.fieldmeta.routes.javascript.FieldMetadataApi.list,
//        controllers.fieldmeta.routes.javascript.FieldMetadataApi.get,
//        controllers.fieldmeta.routes.javascript.FieldMetadataApi.save,
//        controllers.fieldmeta.routes.javascript.FieldMetadataApi.delete,
//        controllers.fieldmeta.routes.javascript.FieldMetadataApi.i18n,
//        controllers.fieldmeta.routes.javascript.FieldMetadataApi.templates,
//        controllers.fieldmeta.routes.javascript.EntityTypeMetadataApi.list,
//        controllers.fieldmeta.routes.javascript.EntityTypeMetadataApi.save,
//        controllers.fieldmeta.routes.javascript.EntityTypeMetadataApi.delete,
//      )
//    ).as(MimeTypes.JAVASCRIPT)
//  }

  def editor(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.admin.fieldmeta.editor())
  }
}
