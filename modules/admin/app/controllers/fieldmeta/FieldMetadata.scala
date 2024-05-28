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

  def editor(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.admin.fieldmeta.editor())
  }

  def list(entityType: Option[EntityType.Value]): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.list(entityType).map { items =>
      Ok(Json.toJson(items))
    }
  }

  def get(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.get(entityType, id).map {
      case Some(item) => Ok(Json.toJson(item))
      case None => NotFound(JsNull)
    }
  }

  def create(entityType: EntityType.Value, id: String): Action[models.FieldMetadataInfo] = WithUserAction.async(parse.json[models.FieldMetadataInfo]) { implicit request =>
    fieldMetaService.create(entityType, id, request.body).map { item =>
      Created(Json.toJson(item))
    }
  }

  def update(entityType: EntityType.Value, id: String): Action[models.FieldMetadataInfo] = WithUserAction.async(parse.json[models.FieldMetadataInfo]) { implicit request =>
    fieldMetaService.update(entityType, id, request.body).map { item =>
      Ok(Json.toJson(item))
    }
  }

  def delete(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.delete(entityType, id).map { success =>
      if (success) NoContent else NotFound
    }
  }

  def templates(): Action[AnyContent] = WithUserAction { implicit request =>
    val data = Json.obj(
      EntityType.RepositoryDescription.toString -> Map(
        "sections" -> Isdiah.SECTIONS.map(section => section -> Messages("repository." + section)),
        "fields" -> Isdiah.FIELDS.map(field => field -> Messages("repository." + field))
      ),
      EntityType.DocumentaryUnitDescription.toString -> Map(
        "sections" -> IsadG.SECTIONS.map(section => section -> Messages("documentaryUnit." + section)),
        "fields" -> IsadG.FIELDS.map(field => field -> Messages("documentaryUnit." + field))
      ),
      EntityType.HistoricalAgentDescription.toString -> Map(
        "sections" -> Isaar.SECTIONS.map(section => section -> Messages("historicalAgent." + section)),
        "fields" -> Isaar.FIELDS.map(field => field -> Messages("historicalAgent." + field))
      )
    )
    Ok(data)
  }
}
