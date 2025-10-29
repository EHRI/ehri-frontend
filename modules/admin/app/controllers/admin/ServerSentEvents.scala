package controllers.admin

import controllers.AppComponents
import controllers.admin.ServerSentEvents.LAST_EVENT_ID_HEADER
import controllers.base.AdminController
import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, Source}
import org.apache.pekko.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import org.apache.pekko.{Done, NotUsed}
import play.api.http.MimeTypes
import play.api.libs.EventSource
import play.api.mvc._
import services.data.ApplicationEventServiceMediator.sseEvent
import services.data.{EventForwarder, ApplicationEventService}

import javax.inject._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ServerSentEvents {
  val LAST_EVENT_ID_HEADER = "Last-Event-Id"
}

/**
  * Controller for Server-Sent Events (SSE) feeds.
  */
@Singleton
case class ServerSentEvents @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  @Named("event-forwarder") forwarder: ActorRef,
  eventStore: ApplicationEventService,
  actorSystem: ActorSystem,
)(implicit mat: Materializer) extends AdminController {

  override val staffOnly = false

  // keep-alive period for event stream
  private val keepAlivePeriod: FiniteDuration = config.get[FiniteDuration]("ehri.eventStream.keepAlive")

  // finish transmitting buffered elements after completion
  private val completionMatcher: PartialFunction[Any, CompletionStrategy] = {
    case Done => CompletionStrategy.draining
  }

  private val (handler: ActorRef, eventSource: Source[EventForwarder.Action, NotUsed]) = Source.actorRef[EventForwarder.Action](
    completionMatcher = completionMatcher,
    failureMatcher = PartialFunction.empty[Any, Throwable], // Never fail this stream
    bufferSize = 100,
    overflowStrategy = OverflowStrategy.dropTail)
    .toMat(BroadcastHub.sink)(Keep.both)
    .run()

  // Subscribe to events...
  forwarder ! EventForwarder.Subscribe(handler)


  /**
    * Add a Server-Send event feed of items created, updated or deleted
    */
  def lifecycle: Action[AnyContent] = Action { implicit request =>

    // If we have a replay header, fetch an initial stream from the store
    val lastInsertId: Option[String] = request.headers.get(LAST_EVENT_ID_HEADER).filter(_.trim.nonEmpty)
    val replay: Future[Seq[EventSource.Event]] = lastInsertId.map { id =>
      eventStore.get(id).map(_.map(ev => EventSource.Event(ev.data, id = Some(ev.id.toString), name = ev.name)))
    }.getOrElse(Future.successful(Seq.empty[EventSource.Event]))
    val replaySource = Source.futureSource(replay.map(Source.apply))

    val events: Source[EventSource.Event, NotUsed] = eventSource.collect {
      case ev: EventForwarder.Action => sseEvent(ev)
    }.collect {
      case Some(event) => event
    }.keepAlive(keepAlivePeriod, () => EventSource.Event(""))

    val stream = replaySource.concat(events)

    Ok.chunked(stream).as(MimeTypes.EVENT_STREAM)
  }
}
