package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import base.{ControllerHelpers, AuthController}
import play.api.data.{FormError, Form}
import play.api.data.Forms._
import defines.{EntityType, PermissionType, ContentType}
import play.Play.application
import rest.{RestPageParams, EntityDAO}
import play.api.libs.iteratee.{Concurrent, Enumerator}
import models.Entity
import models.base.{DescribedEntity, AccessibleEntity}
import play.api.Logger
import play.api.libs.Comet
import concurrent.Future
import solr.SolrIndexer.SolrUpdateResponse
import collection.immutable.IndexedSeq
import solr.SolrIndexer


object SearchAdmin extends Controller with AuthController with ControllerHelpers {

  /**
   * Message that terminates a long-lived streaming response, such
   * as the search index update job.
   */
  val DONE_MESSAGE = "Done"

  /**
   * Render the update form
   * @return
   */
  def updateIndex = adminAction { implicit userOpt => implicit request =>
    Ok(views.html.search.updateIndex(action=routes.SearchAdmin.updateIndexPost))
  }

  /**
   * Perform the actual update, returning a streaming response as the batch
   * jobs complete.
   * @return
   */
  def updateIndexPost = adminAction { implicit userOpt => implicit request =>

    println("Running update: " + request.body.asFormUrlEncoded)
    val batchSize = application.configuration.getInt("solr.update.batchSize")

    import play.api.data.Form
    import play.api.data.Forms._
    import models.forms.enum

    val entities = Form(single("type" -> list(enum(defines.EntityType)))).bindFromRequest.value.get

    def wrapMsg(m: String) = s"<message>$m</message>"

    /**
     * Update a single page of data
     */
    def updatePage(entityType: EntityType.Value, page: rest.Page[Entity], chan: Concurrent.Channel[String]) = {
      solr.SolrIndexer.updateItems(page.items.toStream, commit = false).map { jobs =>
        jobs.map { doneOrErr =>
          // Bail out early if we failed
          if (doneOrErr.isLeft) sys.error("Batch failed: " + doneOrErr.left.get)

          val solrResponse = doneOrErr.right.get

          val msg = s"Batch complete: $entityType (${page.range}}, time: ${solrResponse.time})"
          println(msg)
          Logger.logger.info(msg)
          chan.push(wrapMsg(msg))
          solrResponse
        }
      }
    }

    /**
     * Fetch a given page of data and update it.
     */
    def updateItemSet(entityType: EntityType.Value, pageNum: Int, chan: Concurrent.Channel[String]) = {
      val getData = EntityDAO(entityType, userOpt)
            .page(RestPageParams(page=Some(pageNum), limit = Some(batchSize)))
      getData.flatMap { pageOrErr =>
        // Commit suicide if something went wrong
        if (pageOrErr.isLeft) sys.error("Batch failed: " + pageOrErr.left.get)

        val page = pageOrErr.right.get
        updatePage(entityType, page, chan)
      }
    }

    // Create an unicast channel in which to feed progress messages
    val channel = Concurrent.unicast[String] { chan =>
      var all: List[Future[List[SolrUpdateResponse]]] = entities.map { entity =>
        EntityDAO(entity, userOpt).page(RestPageParams(limit = Some(batchSize))).flatMap { firstPageOrErr =>
          if (firstPageOrErr.isLeft) sys.error(s"Unable to fetch first page of data for $entity: " + firstPageOrErr.left.get)
          val firstPage = firstPageOrErr.right.get

          // Clear all Entities from the index...
          var allUpdateResponses: Future[List[SolrUpdateResponse]] = solr.SolrIndexer.deleteItemsByType(entity, commit = false).flatMap { resOrErr =>
            if (resOrErr.isLeft) sys.error(s"Unsuccessful delete command for $entity: " + resOrErr.left.get)

            // Since we've already fetched a page of data, reuse it immediately
            var page1: Future[List[SolrUpdateResponse]] = updatePage(entity, firstPage, chan)

            // Run the rest in sequence
            var rest: List[Future[List[SolrUpdateResponse]]] = 2.to(firstPage.numPages.toInt).map {
              p =>
                updateItemSet(entity, p.toInt, chan)
            }.toList

            // Flatten the inner batch results into a single list
            Future.sequence(page1 :: rest).map(l => l.flatMap(i => i))
          }
          allUpdateResponses
        }
      }

      // When all updates have finished, commit the results
      Future.sequence(all).map { results =>
        val totaltime = results.flatten.foldLeft(0) { case (total, result) =>
          total + result.time
        }
        chan.push(wrapMsg("Completed indexing in: " + totaltime + " - commiting..."))
        SolrIndexer.commit.map { resOrErr =>
          if (resOrErr.isLeft) sys.error("Error committing Solr data: " + resOrErr.left.get)
          val result = resOrErr.right.get
          chan.push(wrapMsg("Committed in " + result.time))
          chan.push(wrapMsg(DONE_MESSAGE))
          chan.eofAndEnd()
        }
      }
    }

    Ok.stream(channel.andThen(Enumerator.eof))
  }
}