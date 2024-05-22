package controllers.fieldmeta

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import models._
import play.api.http.MimeTypes
import play.api.libs.json.Json
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
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  fieldMetaService: FieldMetadataService,
  ws: WSClient
)(implicit mat: Materializer, executionContext: ExecutionContext) extends AdminController with ApiBodyParsers {

  def jsRoutes(): Action[AnyContent] = Action.apply { implicit request =>
    Ok(
      JavaScriptReverseRouter("fieldmetaApi")(
        controllers.fieldmeta.routes.javascript.FieldMetadata.list,
        controllers.fieldmeta.routes.javascript.FieldMetadata.get,
        controllers.fieldmeta.routes.javascript.FieldMetadata.create,
        controllers.fieldmeta.routes.javascript.FieldMetadata.update,
        controllers.fieldmeta.routes.javascript.FieldMetadata.delete
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  def list(entityType: Option[EntityType.Value]): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.list(entityType).map { items =>
      Ok(Json.toJson(items))
    }
  }

  def get(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.get(entityType, id).map {
      case Some(item) => Ok(Json.toJson(item))
      case None => NotFound
    }
  }

  def create(): Action[models.FieldMetadata] = WithUserAction.async(parse.json[models.FieldMetadata]) { implicit request =>
    fieldMetaService.create(request.body).map { item =>
      Created(Json.toJson(item))
    }
  }

  def update(): Action[models.FieldMetadata] = WithUserAction.async(parse.json[models.FieldMetadata]) { implicit request =>
    fieldMetaService.update(request.body).map { item =>
      Ok(Json.toJson(item))
    }
  }

  def delete(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.delete(entityType, id).map { success =>
      if (success) NoContent else NotFound
    }
  }
}
