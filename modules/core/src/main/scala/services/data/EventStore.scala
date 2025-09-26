package services.data

import com.google.inject.ImplementedBy

import java.util.UUID
import scala.concurrent.Future

case class StoredEvent(data: String, id: UUID, name: Option[String])

/**
  * Service that stores events and facilitates retrieval by last-insert-id.
  */
@ImplementedBy(classOf[SqlEventStore])
trait EventStore {

  def store(events: Seq[StoredEvent]): Future[Int]

  def get(lastInsertId: String): Future[Seq[StoredEvent]]
}
