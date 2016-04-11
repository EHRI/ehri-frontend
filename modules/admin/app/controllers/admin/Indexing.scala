package controllers.admin

import javax.inject._

import akka.actor.{Actor, Props, ActorRef, ActorSystem}
import akka.stream.Materializer
import auth.AccountManager
import backend.DataApi
import backend.rest.cypher.Cypher
import controllers.base.AdminController
import defines.EntityType
import defines.EnumUtils._
import play.api.Logger
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket
import play.api.mvc.WebSocket.MessageFlowTransformer
import utils.MovedPageLookup
import utils.search._
import views.MarkdownRenderer

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

object Indexing {
  /**
   * Message that terminates a long-lived streaming response, such
   * as the search index update job.
   */
  val DONE_MESSAGE = "Done"
  val ERR_MESSAGE = "Index Error"
}

case class IndexTypes(
  types: Seq[EntityType.Value],
  clearAll: Boolean = false,
  clearTypes: Boolean = false
)
object IndexTypes {
  val TYPES = "types"
  val CLEAR_ALL = "clearAll"
  val CLEAR_TYPES = "clearTypes"

  implicit val fmt = Json.format[IndexTypes]
}

case class IndexChildren(id: String, entityType: EntityType.Value, field: String)
object IndexChildren {
  val FIELD = "field"
  val ID = "id"
  val TYPE = "entityType"
  implicit val fmt = Json.format[IndexChildren]
}

@Singleton
case class Indexing @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  searchIndexer: SearchIndexMediator,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher,
  system: ActorSystem,
  mat: Materializer
) extends AdminController {

  // TEST DON'T RELEASE ME!
  override val staffOnly = false

  private def logger = Logger(this.getClass)

  private val indexTypes = Seq(
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
    EntityType.Annotation
  )

  implicit val messageTransformer = MessageFlowTransformer
    .jsonMessageFlowTransformer[JsValue, String]

  object IndexActor {
    def props(out: ActorRef) = Props(new IndexActor(out))
  }

  class IndexActor(out: ActorRef) extends Actor {

    val indexer = searchIndexer.handle.withChannel(out)

    def receive = {
      case js: JsValue if js.validate[IndexTypes].isSuccess =>
        val IndexTypes(types, clearAll, clearTypes) = js.as[IndexTypes]
        val optionallyClearIndex: Future[Unit] =
          if (!clearAll) Future.successful(Unit)
          else indexer.clearAll()

        val optionallyClearType: Future[Unit] =
          if (!clearTypes || clearAll) Future.successful(Unit)
          else indexer.clearTypes(types)

        val job = for {
          _ <- optionallyClearIndex
          _ <- optionallyClearType
          task <- indexer.indexTypes(entityTypes = types)
        } yield task

        job.map { _ =>
          out ! Indexing.DONE_MESSAGE
        } recover {
          case t =>
            logger.logger.error(t.getMessage)
            out ! s"${Indexing.ERR_MESSAGE}: ${t.getMessage}"
        }

      case js: JsValue if js.validate[IndexChildren].isSuccess =>
        val IndexChildren(id, et, field) = js.as[IndexChildren]
        val job = for {
          _ <- indexer.clearKeyValue(field, id)
          _ <- indexer.indexId(id)
          task <- indexer.indexChildren(et, id)
        } yield task

        job map { _ =>
          out ! Indexing.DONE_MESSAGE
        } recover {
          case t =>
            logger.logger.error(t.getMessage)
            out ! s"${Indexing.ERR_MESSAGE}: ${t.getMessage}"
        }

      case JsString(id) =>
        indexer.indexId(id).recover {
          case t => out ! s"${Indexing.ERR_MESSAGE}: ${t.getMessage}"
        }.onComplete { _ =>
          out ! Indexing.DONE_MESSAGE
        }
    }
  }

  import play.api.data.Form
  import play.api.data.Forms._
  import controllers.admin.IndexTypes._

  private val updateIndexForm = Form(
    tuple(
      CLEAR_ALL -> default(boolean, false),
      CLEAR_TYPES -> default(boolean, false),
      TYPES -> list(enumMapping(defines.EntityType))
    )
  )

  def updateIndex() = AdminAction { implicit request =>
    Ok(views.html.admin.search.updateIndex(form = updateIndexForm, types = indexTypes,
      action = controllers.admin.routes.Indexing.indexer()))
  }

  def indexer() = WebSocket.acceptOrResult[JsValue, String] { implicit request =>
    // When connecting we need to authenticate the request...
    // This is currently a bit awkward when not dealing with standard
    // actions, so using play2-auth's `authorized` function to retrieve
    // the user account from the request cookies, then manually fetching
    // the profile and checking it's an admin account.
    authorized(user => true).flatMap {
      case Left(res) => immediate(Left(res))
      case Right((user, _)) => fetchProfile(user).flatMap {
        case Some(prof) if prof.isAdmin => immediate(Right {
          ActorFlow.actorRef(out => IndexActor.props(out))
        })
        // user doesn't have a profile, or it's not admin
        case _ => authorizationFailed(request, user).map(r => Left(r))
      }
    }
  }
}
