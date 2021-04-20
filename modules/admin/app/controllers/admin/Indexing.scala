package controllers.admin

import javax.inject._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import controllers.AppComponents
import controllers.base.AdminController
import models.EntityType
import utils.EnumUtils._
import play.api.Logger
import play.api.libs.json.{Format, JsString, JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.{Action, AnyContent, ControllerComponents, WebSocket}
import services.search._
import utils.WebsocketConstants

import scala.concurrent.Future


case class IndexTypes(
  types: Seq[EntityType.Value],
  clearAll: Boolean = false,
  clearTypes: Boolean = false
)

object IndexTypes {
  val TYPES = "types"
  val CLEAR_ALL = "clearAll"
  val CLEAR_TYPES = "clearTypes"

  implicit val _fmt: Format[IndexTypes] = Json.format[IndexTypes]

  val all = Seq(
    EntityType.Country,
    EntityType.DocumentaryUnit,
    EntityType.HistoricalAgent,
    EntityType.Repository,
    EntityType.Concept,
    EntityType.Vocabulary,
    EntityType.AuthoritativeSet,
    EntityType.UserProfile,
    EntityType.Group,
    EntityType.VirtualUnit,
    EntityType.Annotation,
    EntityType.Link
  )
}

case class IndexChildren(
  id: String,
  entityType: EntityType.Value,
  field: String
)

object IndexChildren {
  val FIELD = "field"
  val ID = "id"
  val TYPE = "entityType"
  implicit val _fmt: Format[IndexChildren] = Json.format[IndexChildren]
}


@Singleton
case class Indexing @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  actorSystem: ActorSystem,
  searchIndexer: SearchIndexMediator
) extends AdminController {

  private def logger = Logger(this.getClass)

  private implicit val messageTransformer: MessageFlowTransformer[JsValue, String] =
    MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, String]

  object IndexActor {
    def props(out: ActorRef) = Props(new IndexActor(out))
  }

  class IndexActor(out: ActorRef) extends Actor {

    private val indexer = searchIndexer.handle.withChannel(out)

    def receive: PartialFunction[Any, Unit] = {
      case js: JsValue if js.validate[IndexTypes].isSuccess =>
        val IndexTypes(types, clearAll, clearTypes) = js.as[IndexTypes]
        val optionallyClearIndex: Future[Unit] =
          if (!clearAll) Future.successful(())
          else indexer.clearAll()

        val optionallyClearType: Future[Unit] =
          if (!clearTypes || clearAll) Future.successful(())
          else indexer.clearTypes(types)

        val job = for {
          _ <- optionallyClearIndex
          _ <- optionallyClearType
          task <- indexer.indexTypes(entityTypes = types)
        } yield task

        job.map( _ => out ! WebsocketConstants.DONE_MESSAGE).recover {
          case t =>
            logger.logger.error(t.getMessage)
            out ! s"${WebsocketConstants.ERR_MESSAGE}: ${t.getMessage}"
        }

      case js: JsValue if js.validate[IndexChildren].isSuccess =>
        val IndexChildren(id, et, field) = js.as[IndexChildren]
        val job = for {
          _ <- indexer.clearKeyValue(field, id)
          _ <- indexer.indexIds(id)
          task <- indexer.indexChildren(et, id)
        } yield task

        job map { _ =>
          out ! WebsocketConstants.DONE_MESSAGE
        } recover {
          case t =>
            logger.logger.error(t.getMessage)
            out ! s"${WebsocketConstants.ERR_MESSAGE}: ${t.getMessage}"
        }

      case JsString(id) =>
        indexer.indexIds(id).recover {
          case t => out ! s"${WebsocketConstants.ERR_MESSAGE}: ${t.getMessage}"
        }.onComplete { _ =>
          out ! WebsocketConstants.DONE_MESSAGE
        }

      case msg => println(s"UNHANDLED: $msg")
    }
  }

  import controllers.admin.IndexTypes._
  import play.api.data.Form
  import play.api.data.Forms._

  private val updateIndexForm = Form(
    tuple(
      CLEAR_ALL -> default(boolean, false),
      CLEAR_TYPES -> default(boolean, false),
      TYPES -> list(enumMapping(models.EntityType))
    )
  )

  def updateIndex(): Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.search.updateIndex(form = updateIndexForm, types = IndexTypes.all,
      action = controllers.admin.routes.Indexing.indexer()))
  }

  def indexer(): WebSocket = AdminWebsocket { implicit request =>
    ActorFlow.actorRef(out => IndexActor.props(out))(actorSystem, appComponents.materializer)
  }
}
