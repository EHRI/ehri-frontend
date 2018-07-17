package global
import defines.EventType
import models.base.{MetaModel, Model}

import scala.concurrent.{ExecutionContext, Future}

case class DefaultItemLifecycle() extends ItemLifecycle {

  private val logger = play.api.Logger(classOf[DefaultItemLifecycle])

  override def preSave[T <: Model](id: Option[String], t: T, event: EventType.Value)(implicit ec: ExecutionContext): Future[T] = {
    logger.info(s"Pre-save: $event: $t")
    Future.successful(t)
  }

  override def postSave[MT <: MetaModel](id: Option[String], mt: MT, t: MT#T, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT] = {
    logger.info(s"Post-save: $event: $t")
    Future.successful(mt)
  }
}
