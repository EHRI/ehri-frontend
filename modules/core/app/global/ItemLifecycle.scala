package global

import com.google.inject.ImplementedBy
import defines.EventType
import models.base.{MetaModel, Model}

import scala.concurrent.{ExecutionContext, Future}


@ImplementedBy(classOf[DefaultItemLifecycle])
trait ItemLifecycle {
  def preSave[T <: Model](id: Option[String], data: T, event: EventType.Value)(implicit ec: ExecutionContext): Future[T]
  def postSave[MT <: MetaModel[T], T <: Model](id: Option[String], saved: MT, pre: T, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT]
}
