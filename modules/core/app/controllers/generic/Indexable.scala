package controllers.generic

import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import play.api.mvc.Controller
import controllers.base.{ControllerHelpers, CoreActionBuilders}
import utils.search.SearchIndexMediator
import scala.concurrent.Future
import scala.util.{Failure, Success}
import play.api.Logger
import backend.Resource


object Indexable {
  /**
   * Message that terminates a long-lived streaming response, such
   * as the search index update job.
   */
  val DONE_MESSAGE = "Done"
  val ERR_MESSAGE = "Index Error"
}

/**
 * Mixin trait for items which can be individually re-indexed, i.e:
 * repositories, vocabularies, countries, etc.
 */
trait Indexable[MT] extends Controller with CoreActionBuilders with ControllerHelpers {

  self: Controller =>

  private def logger = Logger(self.getClass)

  protected def searchIndexer: SearchIndexMediator

  private def wrapMsg(m: String) = s"<message>$m</message>"

  private def finishSuccess(chan: ActorRef) {
    chan ! wrapMsg(Indexable.DONE_MESSAGE)
    chan ! akka.actor.Status.Success(())
  }

  private def finishError(chan: ActorRef, t: Throwable) {
    logger.error(t.getMessage)
    chan ! wrapMsg("Indexing operation failed: " + t.getMessage)
    chan ! wrapMsg(Indexable.ERR_MESSAGE)
    chan ! akka.actor.Status.Success(())
  }


  /**
   * Update a single item in the index.
   *
   * @param id The items id
   * @return An action returning a chunked progress response.
   */
  def updateItemPost(id: String) = AdminAction { implicit request =>
    val source = Source.actorRef[String](1000, OverflowStrategy.dropTail).mapMaterializedValue { chan =>
      searchIndexer.handle.withChannel(chan, wrapMsg).indexId(id).onComplete {
        case Success(()) => finishSuccess(chan)
        case Failure(t) => finishError(chan, t)
      }
    }

    Ok.chunked(source)
  }

  /**
   * Update items parented by this one. The field in the parent docs that
   * refers to this one must be given, i.e. holderId=[this-item]
   * @param field The discriminator field
   * @param id The parent item id
   * @return An action returning a chunked progress response.
   */
  def updateChildItemsPost(field: String, id: String)(implicit rs: Resource[MT]) = AdminAction { implicit request =>
    logger.info(s"Indexing: $searchIndexer")
    val source = Source.actorRef[String](1000, OverflowStrategy.dropTail).mapMaterializedValue { channel =>
      val clearIndex: Future[Unit] = searchIndexer.handle.clearKeyValue(field, id)
        .map ( _ => channel ! wrapMsg("... finished clearing index"))

      val job = clearIndex.flatMap { _ =>
        searchIndexer.handle.withChannel(channel, wrapMsg).indexChildren(rs.entityType, id)
      }

      job.map { _ =>
        finishSuccess(channel)
      } recover {
        case t => finishError(channel, t)
      }
    }

    Ok.chunked(source)
  }
}
