package controllers.admin

import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject._

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.Materializer
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonParseException
import controllers.AppComponents
import controllers.base.AdminController
import defines.EntityType
import models.UserProfile
import models.admin.IngestParams
import models.base.AnyModel
import play.api.Logger
import play.api.data.Form
import play.api.http.{HeaderNames, HttpVerbs}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.libs.streams.ActorFlow
import play.api.libs.ws.WSClient
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.{Action, _}
import services.data.Constants
import services.search._
import utils.WebsocketConstants

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.util.{Failure, Success, Try}


@Singleton
case class Ingest @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator,
  ws: WSClient
)(implicit system: ActorSystem, mat: Materializer) extends AdminController {

  private def logger = Logger(this.getClass)
  import scala.concurrent.duration._

  private implicit val messageTransformer: MessageFlowTransformer[JsValue, String] =
    MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, String]


  object Ticker {
    def props = Props(new Ticker())

    case object Stop
    case object Run
  }

  case class Ticker() extends Actor {
    private val states = Vector("|", "/", "-", "\\")

    override def receive: Receive = init

    def init: Receive = {
      case (actorRef: ActorRef, msg: String) =>
        val cancellable = context.system.scheduler.schedule(500.millis, 1000.millis, self, Ticker.Run)
        context.become(tick(actorRef, msg, 0, cancellable))
    }

    def tick(actorRef: ActorRef, msg: String, state: Int, cancellable: Cancellable): Receive = {
      case Ticker.Run =>
        actorRef ! s"$msg... ${states(state)}"
        context.become(tick(actorRef, msg, if (state < 3) state + 1 else 0, cancellable))
      case Ticker.Stop => cancellable.cancel()
    }
  }

  object IngestActor {

    case class IngestData(
      dataType: String,
      params: IngestParams,
      ct: String,
      file: java.io.File,
      user: UserProfile
    ) {
      def run(): Future[Either[String, JsValue]] = {
        import scala.concurrent.duration._

        logger.info(s"Dispatching ingest: $params")
        ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/import/$dataType")
          .withRequestTimeout(20.minutes)
          .addHttpHeaders(Constants.AUTH_HEADER_NAME -> user.id)
          .addHttpHeaders(HeaderNames.CONTENT_TYPE -> ct)
          .addQueryStringParameters(params.toParams: _*)
          .withMethod(HttpVerbs.POST)
          .withBody(file)
          .stream().flatMap { r =>
          //logger.debug(s"Ingest response: ${r.body}")
          logger.info(s"Got status from ingest of: ${r.status}")
          r.bodyAsSource.runFold(ByteString.empty)(_ ++ _).map{ s =>
            logger.info("Parsing data...")
            try {
              Right(Json.parse(s.toArray))
            } catch {
              case e: JsonParseException => Left(s.utf8String)
            }
          }
        } recover {
          case e: Throwable =>
            e.printStackTrace()
            Left(e.getMessage)
        }
      }
    }

    case class IngestJob(id: String, data: IngestData)

    case class SyncLog(deleted: Seq[String], created: Seq[String], moved: Map[String, String])
    object SyncLog {
      implicit val reads: Reads[SyncLog] = Json.reads[SyncLog]
    }

    case class ErrorLog(error: String, details: String)
    object ErrorLog {
      implicit val reads: Reads[ErrorLog] = Json.reads[ErrorLog]
    }

    def msg(s: String, actorRef: ActorRef): Unit = {
      logger.info(s)
      actorRef ! s
    }
  }

  case class IngestActor() extends Actor {

    import IngestActor._

    private def remapUrlsFromPrefixes(items: Seq[(String, String)], prefixes: String): Seq[(String, String)] = {
      def enc(s: String) = java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name())

      items.flatMap { case (from, to) =>
        prefixes.split(',').map(p => s"$p${enc(from)}" -> s"$p${enc(to)}")
      }
    }

    private def remapMovedUnits(movedIds: Seq[(String, String)]): Future[Int] = {
      val prefixes = Seq(
        controllers.portal.routes.DocumentaryUnits.browse("TEST").url,
        controllers.units.routes.DocumentaryUnits.get("TEST").url
      ).mkString(",").replaceAll("TEST", "")

      val newURLS = remapUrlsFromPrefixes(movedIds, prefixes)
      appComponents.pageRelocator.addMoved(newURLS)
    }

    override def receive: Receive = waiting

    def waiting: Receive = {
      case job: IngestJob =>
        context.become(init(job))
    }

    def init(job: IngestJob): Receive = {
      case monitor: ActorRef =>
        msg(s"Initialising ingest for job: ${job.id}...", monitor)

        val mainTask: Future[Either[String, JsValue]] = job.data.run()

        val ticker = system.actorOf(Ticker.props)
        ticker ! (monitor -> "Ingesting")
        mainTask.onComplete { _ =>
          ticker ! Ticker.Stop
        }

        val allTasks = mainTask.flatMap {
          case Right(json) =>
            val logOpt = json.asOpt[SyncLog]
            logOpt.map { log =>
              msg("Got a valid sync manifest...", monitor)
              msg(s"    - Created: ${log.created.size}, Deleted: ${log.deleted.size}, Moved: ${log.moved.size}", monitor)
              remapMovedUnits(logOpt.get.moved.toSeq).flatMap { num =>
                msg(s"Relocated $num item(s)", monitor)
                msg("Reindexing...", monitor)
                val indexer = searchIndexer.handle.withChannel(monitor)
                indexer.clearKeyValue(SearchConstants.HOLDER_ID, job.data.params.scope).flatMap { _ =>
                  indexer.indexChildren(job.data.params.scopeType, job.data.params.scope)
                }
              }
            }.getOrElse {
              json.asOpt[ErrorLog].fold(msg(Json.stringify(json), monitor)) { err =>
                msg(s"${WebsocketConstants.ERR_MESSAGE}: ${err.details}", monitor)
              }
              Future.successful(())
            }
          case Left(ex) =>
            msg(ex, monitor)
            Future.successful(())
        }

        allTasks.onComplete { _ =>
          msg(WebsocketConstants.DONE_MESSAGE, monitor)
        }

        context.stop(self)
    }
  }

  def ingestPost(scopeType: EntityType.Value, scopeId: String, dataType: String, fonds: Option[String]): Action[MultipartFormData[TemporaryFile]] =
    AdminAction(parse.multipartFormData(Int.MaxValue)).async { implicit request =>

      def showForm(form: Form[IngestParams]): Future[Result] = {
        val scopeItemF: Future[AnyModel] = userDataApi.getAny[AnyModel](scopeId)
        val fondsItemF: Future[Option[AnyModel]] = fonds
          .map(id => userDataApi.getAny[AnyModel](id)
            .map(item => Some(item))).getOrElse(Future.successful(None))

        for {
          scopeItem <- scopeItemF
          fondsItem <- fondsItemF
        } yield BadRequest(views.html.admin.utils
          .ingest(scopeItem, fondsItem, form,
            controllers.admin.routes.Ingest.ingestPost(scopeType, scopeId, dataType, fonds)))
      }

    val boundForm = IngestParams.ingestForm.bindFromRequest()
    request.body.file(IngestParams.DATA_FILE).map { data =>
      boundForm.fold(
        errForm => showForm(errForm),
        ingestTask => {
          // We only want XML types here, everything else is just binary
          val ct = data.contentType.filter(_.endsWith("xml"))
            .getOrElse(play.api.http.ContentTypes.BINARY)
          // NB: Overcomplicated due to https://github.com/playframework/playframework/issues/6203
          val props: Option[java.io.File] = request.body.file(IngestParams.PROPERTIES_FILE)
            .flatMap(f => if (f.filename.nonEmpty) Some(f.ref.path.toFile) else None)

          val task = ingestTask.copy(properties = props)
          val ingest = IngestActor.IngestData(dataType, task, ct, data.ref.path.toFile, request.user)
          val jobId = UUID.randomUUID().toString

          val runner = system.actorOf(Props(IngestActor()), jobId)
          runner ! IngestActor.IngestJob(jobId, ingest)

          immediate {
            if (isAjax) Ok(Json.obj(
              "url" -> controllers.admin.routes.Ingest.ingestMonitorWS(jobId).webSocketURL(request.secure),
              "jobId" -> jobId
            ))
            else Redirect(controllers.admin.routes.Ingest.ingestMonitor(jobId))
          }
        }
      )
    }.getOrElse(showForm(boundForm.withError(IngestParams.DATA_FILE, "required")))
  }

  def ingestMonitor(jobId: String): Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.ingest.ingestMonitor(controllers.admin.routes.Ingest.ingestMonitorWS(jobId)))
  }

  def ingestMonitorWS(jobId: String): WebSocket = AdminWebsocket { implicit request =>
    ActorFlow.actorRef { out =>
      system.actorSelection("user/" + jobId).resolveOne(5.seconds).onComplete {
        case Success(ref) => ref ! out
        case Failure(ex) => out ! s"No running job found with id: $jobId."
      }

      Props(IngestActor())
    }
  }
}
