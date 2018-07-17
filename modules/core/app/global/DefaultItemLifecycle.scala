package global
import defines.EventType
import models.base.{Model, ModelData}

import scala.concurrent.{ExecutionContext, Future}

case class DefaultItemLifecycle() extends ItemLifecycle {

  private val logger = play.api.Logger(classOf[DefaultItemLifecycle])

  override def preSave[T <: ModelData](id: Option[String], t: T, event: EventType.Value)(implicit ec: ExecutionContext): Future[T] = {
    logger.info(s"Pre-save: $event: $t")
    Future.successful(t)
  }

  override def postSave[MT <: Model](id: Option[String], mt: MT, t: MT#T, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT] = {
    logger.info(s"Post-save: $event: $t")
    Future.successful(mt)
  }
}
