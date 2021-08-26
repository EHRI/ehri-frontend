package controllers.datasets

import actors.LongRunningJob
import akka.actor.ActorNotFound
import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Update
import models._
import play.api.Logger
import play.api.libs.json.{JsNull, Json}
import play.api.mvc._

import javax.inject.{Inject, Singleton}

@Singleton
case class LongRunningJobs @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
)(implicit mat: Materializer) extends AdminController with Update[Repository] {

  private val logger = Logger(this.getClass)

  def cancel(id: String, jobId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._
    mat.system.actorSelection("user/" + jobId).resolveOne(5.seconds).map { ref =>
      logger.info(s"Cancelling job: $jobId")
      ref ! LongRunningJob.Cancel
      Ok(Json.obj("ok" -> true))
    }.recover {
      // Check if job is already cancelled or missing...
      case _: ActorNotFound => Ok(Json.obj("ok" -> JsNull));
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }
}
