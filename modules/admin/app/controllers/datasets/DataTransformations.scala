package controllers.datasets

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Update
import models._
import play.api.libs.json.Json
import play.api.mvc._
import services.transformation._

import javax.inject.{Inject, Singleton}

@Singleton
case class DataTransformations @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataTransformations: DataTransformationService,
)(implicit mat: Materializer) extends AdminController with ApiBodyParsers with StorageHelpers with Update[Repository] {

  def list(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.list()
      .map(_.filter(dt => dt.repoId.isEmpty || dt.repoId.contains(id)))
      .map(dts => Ok(Json.toJson(dts)))
  }

  def get(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.get(dtId).map(dt => Ok(Json.toJson(dt)))
  }

  def create(id: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(apiJson[DataTransformationInfo]) { implicit request =>
    dataTransformations.create(request.body, if (generic) None else Some(id)).map { dt =>
      Created(Json.toJson(dt))
    }.recover {
      case e: DataTransformationExists => BadRequest(e)
    }
  }

  def update(id: String, dtId: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(apiJson[DataTransformationInfo]) { implicit request =>
    dataTransformations.update(dtId, request.body, if (generic) None else Some(id)).map { dt =>
      Ok(Json.toJson(dt))
    }.recover {
      case e: DataTransformationExists => BadRequest(e)
    }
  }

  def delete(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.delete(dtId).map(_ => NoContent)
  }
}
