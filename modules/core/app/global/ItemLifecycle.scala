package global

import com.google.inject.ImplementedBy
import defines.EventType
import models.base.{Model, ModelData}

import scala.concurrent.{ExecutionContext, Future}


@ImplementedBy(classOf[DefaultItemLifecycle])
trait ItemLifecycle {
  def preSave[T <: ModelData](id: Option[String], data: T, event: EventType.Value)(implicit ec: ExecutionContext): Future[T]
  def postSave[MT <: Model](id: Option[String], saved: MT, pre: MT#T, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT]
}
