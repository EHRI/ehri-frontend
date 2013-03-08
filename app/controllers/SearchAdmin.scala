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
import solr.SolrIndexer.{SolrErrorResponse, SolrResponse, SolrUpdateResponse}
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
   *
   * FIXME: In order to comprehend the flow of this stuff error handling has
   * been thrown out the window, but we should fix this at some point.
   *
   * @return
   */
  def updateIndexPost = adminAction { implicit userOpt => implicit request =>

    val batchSize = application.configuration.getInt("solr.update.batchSize")

    import play.api.data.Form
    import play.api.data.Forms._
    import models.forms.enum

    val entities = Form(single("type" -> list(enum(defines.EntityType)))).bindFromRequest.value.get

    def wrapMsg(m: String) = s"<message>$m</message>"

    /**
     * Update a single page of data
     */
    def updatePage(entityType: EntityType.Value, page: rest.Page[Entity], chan: Concurrent.Channel[String]
                    ): Future[List[SolrResponse]] = {
      solr.SolrIndexer.updateItems(page.items.toStream, commit = false).map { jobs =>
        jobs.map { response =>
          response match {
            case e: SolrErrorResponse => {
              Logger.logger.error(s"Unable to page page data for entity: $entityType, page: ${page.page}: {}", e.err)
              e
            }
            case ok: SolrUpdateResponse => {
              val msg = s"Batch complete: $entityType (${page.range}}, time: ${ok.time})"
              Logger.logger.info(msg)
              chan.push(wrapMsg(msg))
              ok
            }
          }
        }
      }
    }

    /**
     * Fetch a given page of data and update it.
     */
    def updateItemSet(entityType: EntityType.Value, pageNum: Int,
                      chan: Concurrent.Channel[String]): Future[List[SolrResponse]] = {
      val getData = EntityDAO(entityType, userOpt)
            .page(RestPageParams(page=Some(pageNum), limit = Some(batchSize)))
      getData.flatMap { pageOrErr =>
        pageOrErr match {
          case Left(err) => {
            Logger.logger.error(s"Unable to page page data for entity: $entityType, page: ${pageNum}: {}", err)
            Future.successful(List(SolrErrorResponse(pageOrErr.left.get)))
          }
          case Right(page) => updatePage(entityType, pageOrErr.right.get, chan)
        }
      }
    }

    // Create an unicast channel in which to feed progress messages
    val channel = Concurrent.unicast[String] { chan =>
      var all: List[Future[List[SolrResponse]]] = entities.map { entity =>
        EntityDAO(entity, userOpt).page(RestPageParams(limit = Some(batchSize))).flatMap { firstPageOrErr =>
          if (firstPageOrErr.isLeft) sys.error(s"Unable to fetch first page of data for $entity: " + firstPageOrErr.left.get)
          val firstPage = firstPageOrErr.right.get

          // Clear all Entities from the index...
          var allUpdateResponses: Future[List[SolrResponse]] = solr.SolrIndexer.deleteItemsByType(entity, commit = false).flatMap { response =>
            response match {
              case e: SolrErrorResponse => {
                chan.push(s"Error deleting items for entity: $entity: ${e.err}")
                Future.successful(List(response))
              }
              case ok => {
                // Since we've already fetched a page of data, reuse it immediately
                var page1: Future[List[SolrResponse]] = updatePage(entity, firstPage, chan)

                // Run the rest in sequence
                var rest: List[Future[List[SolrResponse]]] = 2.to(firstPage.numPages.toInt).map {
                  p =>
                    updateItemSet(entity, p.toInt, chan)
                }.toList

                // Flatten the inner batch results into a single list
                Future.sequence(page1 :: rest).map(l => l.flatMap(i => i))
              }
            }
          }
          allUpdateResponses
        }
      }

      // When all updates have finished, commit the results
      Future.sequence(all).map { results =>
        val totaltime = results.flatten.foldLeft(0) { case (total, result) =>
          result match {
            case r: SolrUpdateResponse => total + r.time
            case e => total
          }
        }
        chan.push(wrapMsg("Completed indexing in: " + totaltime + " - committing..."))
        SolrIndexer.commit.map { resOrErr =>
          resOrErr match {
            case e: SolrErrorResponse => {
              chan.push("Error committing Solr data: " + e.err)
            }
            case ok: SolrUpdateResponse => {
              chan.push(wrapMsg("Committed in " + ok.time))
            }
          }
          chan.push(wrapMsg(DONE_MESSAGE))
          chan.eofAndEnd()
        }
      }
    }

    Ok.stream(channel.andThen(Enumerator.eof))
  }
}