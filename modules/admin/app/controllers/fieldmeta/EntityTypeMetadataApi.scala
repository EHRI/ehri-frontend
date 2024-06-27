package controllers.fieldmeta

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import models._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.data.DataHelpers
import services.fieldmeta.EntityTypeMetadataService

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

  def list(entityType: Option[EntityType.Value]): Action[AnyContent] = WithUserAction.async { implicit request =>
    entityTypeMetaService.list(entityType).map { items =>
      Ok(Json.toJson(items.map(p => p._1.toString -> p._2)))
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
}
