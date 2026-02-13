package services.data

import models.EntityType
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef}
import play.api.libs.EventSource
import play.api.libs.json.Json
import services.data.EventForwarder.{Create, Delete, Update}

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ApplicationEventServiceMediator {

  private case class Stored(count: Int)

  val CREATE_EVENT = "create-event"
  val UPDATE_EVENT = "update-event"
  val DELETE_EVENT = "delete-event"

  // Generate a Server-Send Events (SSE) payload...
  private def toPayload(items: Seq[(EntityType.Value, String)], uuid: UUID, name: String): EventSource.Event = EventSource.Event(
    Json.stringify(Json.obj("datetime" -> Instant.now(), "ids" -> items.map(_._2), "types" -> items.map(_._1))),
    id = Some(uuid.toString),
    name = Some(name),
  )

  /**
    * Convert an action from the event forwarder, to an SSE payload.
    */
  def sseEvent: PartialFunction[EventForwarder.Action, Option[EventSource.Event]] = {
    case Create(items, uuid) if items.nonEmpty => Some(toPayload(items, uuid, CREATE_EVENT))
    case Update(items, uuid) if items.nonEmpty => Some(toPayload(items, uuid, UPDATE_EVENT))
    case Delete(items, uuid) if items.nonEmpty => Some(toPayload(items, uuid, DELETE_EVENT))
  }
}

/**
  * This actor subscribes to the Event Feed on construction and
  * stores any events received in the event store.
  *
  * @param eventService the application event service
  * @param forwarder    the event feed actor
  */
@Singleton
class ApplicationEventServiceMediator @Inject()(
  eventService: ApplicationEventService,
  @Named("event-forwarder") forwarder: ActorRef
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import ApplicationEventServiceMediator._
  import org.apache.pekko.pattern.pipe

  // When we get constructed, subscribe to the EventForwarder's event feed...
  forwarder ! EventForwarder.Subscribe(self)

  override def receive: Receive = {
    case ev: EventForwarder.Action =>
      log.debug("Received an action: {}", ev)
      sseEvent(ev).map { event =>
        eventService.save(Seq(ApplicationEvent(id = ev.uuid, event.data, name = event.name)))
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
