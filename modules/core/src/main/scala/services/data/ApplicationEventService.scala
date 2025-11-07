package services.data

import com.google.inject.ImplementedBy

import java.util.UUID
import scala.concurrent.Future

case class ApplicationEvent(id: UUID, data: String, name: Option[String])

/**
  * Service that stores events and facilitates retrieval by last-insert-id.
  */
@ImplementedBy(classOf[SqlApplicationEventService])
trait ApplicationEventService {

  def save(events: Seq[ApplicationEvent]): Future[Int]

  def get(lastInsertId: String): Future[Seq[ApplicationEvent]]
}
