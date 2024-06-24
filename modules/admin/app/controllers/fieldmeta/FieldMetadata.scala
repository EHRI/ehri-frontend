package controllers.fieldmeta

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import models._
import play.api.http.MimeTypes
import play.api.i18n.Messages
import play.api.libs.json.{JsNull, Json}
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import services.data.DataHelpers
import services.fieldmeta.FieldMetadataService

import javax.inject._
import scala.concurrent.ExecutionContext



@Singleton
case class FieldMetadata @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents
) extends AdminController with ApiBodyParsers {

  def jsRoutes(): Action[AnyContent] = Action.apply { implicit request =>
    Ok(
      JavaScriptReverseRouter("fieldMetadataApi")(
        controllers.fieldmeta.routes.javascript.FieldMetadataApi.list,
        controllers.fieldmeta.routes.javascript.FieldMetadataApi.get,
        controllers.fieldmeta.routes.javascript.FieldMetadataApi.save,
        controllers.fieldmeta.routes.javascript.FieldMetadataApi.delete
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  def editor(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.admin.fieldmeta.editor())
  }
}
