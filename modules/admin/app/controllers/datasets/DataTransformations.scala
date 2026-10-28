package controllers.datasets

import org.apache.pekko.stream.Materializer
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import controllers.generic.Update
import models._
import play.api.libs.json.Json
import play.api.mvc._
import services.data.ItemNotFound
import services.transformation._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
case class DataTransformations @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataTransformations: DataTransformationService,
)(implicit mat: Materializer) extends AdminController with ApiBodyParsers with StorageHelpers with Update[Repository] {

  // Transformations belong either to a single repository, or are generic
  // (shared by all repositories.) Users who can update a repository can use
  // its own and the generic transformations (the only ones the service
  // returns for it), but only change its own: changing generic ones affects
  // all repositories, so requires an admin.
  private def canWrite(generic: Boolean)(implicit request: EditRequest[_]): Boolean =
    !generic || request.userOpt.exists(_.isAdmin)

  private def forbidden(implicit request: EditRequest[_]): Future[Result] =
    request.userOpt.map(authorizationFailed(request, _)).getOrElse(authenticationFailed(request))

  private def notFound(implicit request: EditRequest[_]): PartialFunction[Throwable, Future[Result]] = {
    case e: ItemNotFound => notFoundError(request, e.value)
  }

  def list(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.list(id).map(dts => Ok(Json.toJson(dts)))
  }

  def get(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.get(id, dtId).map(dt => Ok(Json.toJson(dt))).recoverWith(notFound)
  }

  def create(id: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(apiJson[DataTransformationInfo]) { implicit request =>
    if (!canWrite(generic)) forbidden
    else dataTransformations.create(request.body, if (generic) None else Some(id)).map { dt =>
      Created(Json.toJson(dt))
    }.recover {
      case e: DataTransformationExists => BadRequest(e)
    }
  }

  def update(id: String, dtId: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(apiJson[DataTransformationInfo]) { implicit request =>
    dataTransformations.get(id, dtId).flatMap { existing =>
      if (!canWrite(existing.repoId.isEmpty) || !canWrite(generic)) forbidden
      else dataTransformations.update(dtId, request.body, if (generic) None else Some(id)).map { updated =>
        Ok(Json.toJson(updated))
      }.recover {
        case e: DataTransformationExists => BadRequest(e)
      }
    }.recoverWith(notFound)
  }

  def delete(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.get(id, dtId).flatMap { existing =>
      if (!canWrite(existing.repoId.isEmpty)) forbidden
      else dataTransformations.delete(dtId).map(_ => NoContent)
    }.recoverWith(notFound)
  }
}
