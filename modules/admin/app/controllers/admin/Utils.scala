package controllers.admin

import controllers.AppComponents
import controllers.base.AdminController
import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, Source}
import org.apache.pekko.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import org.apache.pekko.{Done, NotUsed}
import play.api.http.MimeTypes
import play.api.libs.EventSource
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.cypher.CypherService
import services.data.EventStoreMediator.sseEvent
import services.data.{AuthenticatedUser, EventForwarder, EventStore}
import services.ingest.EadValidator
import services.search.SearchIndexMediator
import services.storage.FileStorage
import utils.PageParams

import javax.inject._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration


/**
  * Controller for various monitoring functions and admin utilities.
  */
@Singleton
case class Utils @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator,
  ws: WSClient,
  eadValidator: EadValidator,
  cypher: CypherService,
  @Named("dam") storage: FileStorage,
  @Named("event-forwarder") forwarder: ActorRef,
  eventStore: EventStore,
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
  def sse: Action[AnyContent] = Action { implicit request =>

    // If we have a replay header, fetch an initial stream from the store
    val lastInsertId: Option[String] = request.headers.get("Last-Event-Id").filter(_.trim.nonEmpty)
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

  /** Check the database is up by trying to load the admin account.
    */
  def checkServices: Action[AnyContent] = Action.async { implicit request =>
    val checkDbF = dataApi.withContext(AuthenticatedUser("admin")).status()
      .recover { case e => s"ko: ${e.getMessage}" }.map(s => s"ehri\t$s")
    val checkSearchF = searchEngine.status()
      .recover { case e => s"ko: ${e.getMessage}" }.map(s => s"solr\t$s")

    Future.sequence(Seq(checkDbF, checkSearchF)).map(_.mkString("\n")).map { s =>
      if (s.contains("ko")) ServiceUnavailable(s) else Ok(s)
    }
  }

  /** Check users in the accounts DB have profiles in
    * the graph DB, and vice versa.
    */
  def checkUserSync: Action[AnyContent] = Action.async { implicit request =>

    for {
      allAccounts <- accounts.findAll(PageParams.empty.withoutLimit)
      profileIds <- cypher.get("MATCH (n:UserProfile) RETURN n.__id").map {
        res => res.data.collect { case JsString(id) :: _ => id }.flatten
      }
      accountIds = allAccounts.map(_.id)
    } yield {
      val noProfile = accountIds.diff(profileIds)
      // Going nicely imperative here - sorry!
      var out = ""
      if (noProfile.nonEmpty) {
        out += "Users have account but no profile\n"
        noProfile.foreach { u =>
          out += s"  $u\n"
        }
      }
      Ok(out)
    }
  }
}
