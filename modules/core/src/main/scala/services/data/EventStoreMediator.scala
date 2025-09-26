package services.data

import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef}
import play.api.libs.EventSource
import play.api.libs.json.Json
import services.data.EventForwarder.{Create, Delete, Update}

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object EventStoreMediator {

  private case class Stored(count: Int)

  // Generate a Server-Send Events (SSE) payload...
  private def toPayload(ids: Seq[String], uuid: UUID, name: String): EventSource.Event = EventSource.Event(
    Json.stringify(Json.obj("datetime" -> Instant.now(), "ids" -> ids)),
    id = Some(uuid.toString),
    name = Some(name),
  )

  /**
    * Convert an action from the event forwarder, to an SSE payload.
    */
  def sseEvent: PartialFunction[EventForwarder.Action, Option[EventSource.Event]] = {
    case Create(ids, uuid) if ids.nonEmpty => Some(toPayload(ids, uuid, "create-event"))
    case Update(ids, uuid) if ids.nonEmpty => Some(toPayload(ids, uuid, "update-event"))
    case Delete(ids, uuid) if ids.nonEmpty => Some(toPayload(ids, uuid, "delete-event"))
  }
}

@Singleton
class EventStoreMediator @Inject()(
  eventStore: EventStore,
  @Named("event-forwarder") forwarder: ActorRef
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
  import EventStoreMediator._
  import org.apache.pekko.pattern.pipe

  // When we get constructed, subscribe to the EventForwarder's event feed...
  forwarder ! EventForwarder.Subscribe(self)

  override def receive: Receive = {
    case ev: EventForwarder.Action =>
      log.debug("Received an action: {}", ev)
      sseEvent(ev).map { event =>
        eventStore.store(Seq(StoredEvent(event.data, id = ev.uuid, name = event.name)))
          .map(Stored)
          .pipeTo(self)
          .onComplete {
            case Failure(exception) => log.error(exception, s"Error storing event: $ev")
            case Success(Stored(count)) => log.debug("Event successfully stored (count: {}): {}", count, ev)
          }
      }

    case Stored(count) =>
      log.debug("Stored {} events", count)
  }
}
