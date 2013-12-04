package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import controllers.base.{ControllerHelpers, AuthController}
import utils.search.Indexer
import models.json.RestResource
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.iteratee.{Enumerator, Concurrent}
import scala.concurrent.Future
import scala.util.{Failure, Success}
import play.api.Logger
import solr.SolrConstants

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
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Indexable[MT] extends Controller with AuthController with ControllerHelpers {
  implicit val resource: RestResource[MT]
  def searchIndexer: Indexer

  /**
   * This field is used as the key for clearing items, i.e
   * everything using this trait should have its child items
   * refer to it as holderId=this-id
   */
  val DISCRIMINATOR = SolrConstants.HOLDER_ID

  def updateIndexPost(id: String) = adminAction { implicit userOpt => implicit request =>

    def wrapMsg(m: String) = s"<message>$m</message>"

    // Create an unicast channel in which to feed progress messages
    val channel = Concurrent.unicast[String] { chan =>

      def clearIndex: Future[Unit] = {
        val f = searchIndexer.clearKeyValue(DISCRIMINATOR, id)
        f.onSuccess {
          case () => chan.push(wrapMsg("... finished clearing index"))
        }
        f
      }

      val job = clearIndex.flatMap { _ =>
        searchIndexer.withChannel(chan, wrapMsg).indexChildren(resource.entityType, id)
      }

      job.onComplete {
        case Success(()) => {
          chan.push(wrapMsg(Indexable.DONE_MESSAGE))
          chan.eofAndEnd()
        }
        case Failure(t) => {
          Logger.logger.error(t.getMessage)
          chan.push(wrapMsg("Indexing operation failed: " + t.getMessage))
          chan.push(wrapMsg(Indexable.ERR_MESSAGE))
          chan.eofAndEnd()
        }
      }
    }

    Ok.chunked(channel.andThen(Enumerator.eof))
  }

}
