package controllers.datasets

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Update
import models._
import play.api.Logger
import play.api.libs.json.JsError.toJson
import play.api.libs.json.{Json, Reads}
import play.api.mvc._
import services.transformation._

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration

@Singleton
case class DataTransformations @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataTransformations: DataTransformationService,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  private val logger: Logger = Logger(classOf[DataTransformations])
  private val cacheTime: Duration = appComponents.config.get[Duration]("ehri.admin.dataManager.cacheExpiration")

  // To override the max request size we unfortunately need to define our own body parser here:
  // The max value is drawn from config:
  private def json[A](implicit reader: Reads[A]): BodyParser[A] = BodyParser { request =>
    val max = config.get[Long]("ehri.admin.dataManager.maxTransformationSize")
    parse.json(max)(request).map {
      case Left(simpleResult) => Left(simpleResult)
      case Right(jsValue) =>
        jsValue.validate(reader).map { a =>
          Right(a)
        } recoverTotal { jsError =>
          Left(BadRequest(Json.obj("error" -> "invalid", "details" -> toJson(jsError))))
        }
    }
  }

  def list(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.list()
      .map(_.filter(dt => dt.repoId.isEmpty || dt.repoId.contains(id)))
      .map(dts => Ok(Json.toJson(dts)))
  }

  def get(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.get(dtId).map(dt => Ok(Json.toJson(dt)))
  }

  def create(id: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(json[DataTransformationInfo]) { implicit request =>
    dataTransformations.create(request.body, if (generic) None else Some(id)).map { dt =>
      Created(Json.toJson(dt))
    }.recover {
      case e: DataTransformationExists => BadRequest(e)
    }
  }

  def update(id: String, dtId: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(json[DataTransformationInfo]) { implicit request =>
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
