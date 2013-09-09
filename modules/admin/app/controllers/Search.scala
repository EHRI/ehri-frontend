package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import controllers.base.EntitySearch
import play.Play.application
import play.api.libs.iteratee.{Concurrent, Enumerator}
import models.IsadG
import concurrent.Future
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.{Writes, Json}

import com.google.inject._
import solr.facet.FieldFacetClass
import scala.Some
import models.base.AnyModel
import utils.search.{SearchParams, SearchOrder}


object Search {
  /**
   * Message that terminates a long-lived streaming response, such
   * as the search index update job.
   */
  val DONE_MESSAGE = "Done"
}

@Singleton
class Search @Inject()(implicit val globalConfig: global.GlobalConfig, val searchIndexer: indexing.NewIndexer) extends EntitySearch {

  val searchEntities = List() // i.e. Everything
  private val entityFacets = List(
    FieldFacetClass(
      key=IsadG.LANG_CODE,
      name=Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
      param="lang",
      render=Helpers.languageCodeToName
    ),
    FieldFacetClass(
      key="type",
      name=Messages("search.type"),
      param="type",
      render=s => Messages("contentTypes." + s)
    ),
    FieldFacetClass(
      key="copyrightStatus",
      name=Messages("copyrightStatus.copyright"),
      param="copyright",
      render=s => Messages("copyrightStatus." + s)
    ),
    FieldFacetClass(
      key="scope",
      name=Messages("scope.scope"),
      param="scope",
      render=s => Messages("scope." + s)
    )
  )

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def search = searchAction[AnyModel](
    defaultParams = Some(SearchParams(sort = Some(SearchOrder.Score))),
    entityFacets = entityFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Secured {
      render {
        case Accepts.Json() => {
          Ok(Json.toJson(Json.obj(
            "numPages" -> page.numPages,
            "page" -> Json.toJson(page.items.map(_._1))(Writes.seq(AnyModel.Converter.clientFormat)),
            "facets" -> facets
          ))
          )
        }
        case _ => Ok(views.html.search.search(page, params, facets,
          controllers.admin.routes.Search.search))
      }
    }
  }

  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   * @return
   */
  def filter = filterAction() { page => implicit userOpt => implicit request =>
    Ok(Json.obj(
      "numPages" -> page.numPages,
      "page" -> page.page,
      "items" -> page.items.map { case (id, name, t) =>
        Json.arr(id, name, t.toString)
      }
    ))
  }



  import play.api.data.Form
  import play.api.data.Forms._
  import models.forms.enum

  private lazy val defaultBatchSize: Int
      = application.configuration.getInt("solr.update.batchSize")

  private val updateIndexForm = Form(
    tuple(
      "all" -> default(boolean, false),
      "batchSize" -> default(number, defaultBatchSize),
      "type" -> list(enum(defines.EntityType))
    )
  )

  /**
   * Render the update form
   * @return
   */
  def updateIndex = adminAction { implicit userOpt => implicit request =>
    Ok(views.html.search.updateIndex(form = updateIndexForm,
        action = controllers.admin.routes.Search.updateIndexPost))
  }

  /**
   * Perform the actual update, returning a streaming response as the batch
   * jobs complete.
   *
   * FIXME: In order to comprehend the flow of this stuff error handling has
   * been thrown out the window, but we should fix this at some point.
   *
   * @return
   */
  def updateIndexPost = adminAction { implicit userOpt => implicit request =>

    val (deleteAll, batchSize, entities) = updateIndexForm.bindFromRequest.value.get

    def wrapMsg(m: String) = s"<message>$m</message>"

    println("Index update POST...")

    /**
     * Clear everything from the index...
     */
    def optionallyClearIndex(doit: Boolean): Future[Option[String]] = {
      if (!doit) Future.successful(None)
      else searchIndexer.clearAll
    }

    // Create an unicast channel in which to feed progress messages
    val channel = Concurrent.unicast[String] { chan =>

      val clear = optionallyClearIndex(deleteAll)
      clear.onFailure {
        case err => chan.push("FAILED FAILED FAILED FAILED: " + err.getMessage)
      }

      println("Running clear job...")
      clear.flatMap { maybeResponse =>
        println("Clearing index...")
        val msg = maybeResponse.map { r =>
          s"Error deleting index: $r"
        } getOrElse {
          "Finished clearing index..."
        }
        chan.push(wrapMsg(msg))

        case class Counter(var i: Long = 0) {
          def count(item: String) {
            i += 1
            if (i % 1000 == 0) {
              chan.push(wrapMsg(" ... " + i))
            }
          }
        }

        // Now get on with the real work...
        chan.push(wrapMsg(s"Initiating update for entities: ${entities.mkString(", ")}"))
        val counter = new Counter()
        val job = searchIndexer.indexTypes(entityTypes = entities, counter.count)
        job.onFailure {
          case err => chan.push("FAILED FAILED FAILED FAILED: " + err.getMessage)
        }
        job.map { stream =>
          (stream.map(Some(_)) #::: Stream[Option[String]](None)).foreach {
            case Some(msg) =>
            case None => {
              chan.push(wrapMsg("Completed: " + counter.i))
              chan.push(wrapMsg(Search.DONE_MESSAGE))
              chan.eofAndEnd()
            }
          }
        }
      }
    }

    Ok.stream(channel.andThen(Enumerator.eof))
  }
}
