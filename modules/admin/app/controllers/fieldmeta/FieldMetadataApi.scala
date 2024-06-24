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
case class FieldMetadataApi @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  fieldMetaService: FieldMetadataService,
  ws: WSClient
)(implicit mat: Materializer, executionContext: ExecutionContext) extends AdminController with ApiBodyParsers {

  def list(entityType: Option[EntityType.Value]): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.list(entityType).map { items =>
      Ok(Json.toJson(items.toMap))
    }
  }

  def get(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.get(entityType, id).map {
      case Some(item) => Ok(Json.toJson(item))
      case None => NotFound(JsNull)
    }
  }

  def save(entityType: EntityType.Value, id: String): Action[models.FieldMetadataInfo] = WithUserAction.async(parse.json[models.FieldMetadataInfo]) { implicit request =>
    fieldMetaService.save(entityType, id, request.body).map { item =>
      Ok(Json.toJson(item))
    }
  }

  def delete(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    fieldMetaService.delete(entityType, id).map { success =>
      if (success) NoContent else NotFound
    }
  }

  def i18n(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(Json.toJson(messagesApi.messages))
  }

  def templates(): Action[AnyContent] = WithUserAction { implicit request =>
    val data = Json.obj(
      EntityType.Country.toString -> Seq("" -> Seq("report", "situation", "summary", "extensive")),
      EntityType.RepositoryDescription.toString -> Isdiah.FIELDS,
      EntityType.DocumentaryUnitDescription.toString -> IsadG.FIELDS,
      EntityType.HistoricalAgentDescription.toString -> Isaar.FIELDS
    )
    Ok(data)
  }
}
