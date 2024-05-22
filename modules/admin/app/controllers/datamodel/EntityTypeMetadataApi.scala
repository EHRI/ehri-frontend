package controllers.datamodel

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import models._
import play.api.libs.json.{JsNull, Json}
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.data.DataHelpers
import services.datamodel.EntityTypeMetadataService

import javax.inject._
import scala.concurrent.ExecutionContext


@Singleton
case class EntityTypeMetadataApi @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  entityTypeMetaService: EntityTypeMetadataService,
  ws: WSClient
)(implicit mat: Materializer, executionContext: ExecutionContext) extends AdminController with ApiBodyParsers {

  def list(): Action[AnyContent] = WithUserAction.async { implicit request =>
    entityTypeMetaService.list().map { items =>
      Ok(Json.toJson(items.map(p => p._1.toString -> p._2)))
    }
  }

  def get(entityType: EntityType.Value): Action[AnyContent] = WithUserAction.async { implicit request =>
    entityTypeMetaService.get(entityType).map { item =>
      Ok(Json.toJson(item))
    }
  }

  def save(entityType: EntityType.Value): Action[EntityTypeMetadataInfo] = WithUserAction.async(parse.json[EntityTypeMetadataInfo]) { implicit request =>
    entityTypeMetaService.save(entityType, request.body).map { item =>
      Ok(Json.toJson(item))
    }
  }

  def delete(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    entityTypeMetaService.delete(entityType).map { success =>
      if (success) NoContent else NotFound
    }
  }

  def listFields(entityType: Option[EntityType.Value]): Action[AnyContent] = WithUserAction.async { implicit request =>
    entityTypeMetaService.listFields(entityType).map { items =>
      Ok(Json.toJson(items.map(p => p._1.toString -> p._2)))
    }
  }

  def getField(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    entityTypeMetaService.getField(entityType, id).map {
      case Some(item) => Ok(Json.toJson(item))
      case None => NotFound(JsNull)
    }
  }

  def saveField(entityType: EntityType.Value, id: String): Action[models.FieldMetadataInfo] = WithUserAction.async(parse.json[models.FieldMetadataInfo]) { implicit request =>
    entityTypeMetaService.saveField(entityType, id, request.body).map { item =>
      Ok(Json.toJson(item))
    }
  }

  def deleteField(entityType: EntityType.Value, id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    entityTypeMetaService.deleteField(entityType, id).map { success =>
      if (success) NoContent else NotFound
    }
  }

  def i18n(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(Json.toJson(messagesApi.messages))
  }

  def templates(): Action[AnyContent] = WithUserAction.async { implicit request =>
    entityTypeMetaService.templates().map { tpl =>
      Ok(Json.toJson(tpl.map(p => p._1.toString -> p._2)))
    }
  }
}
