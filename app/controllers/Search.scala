package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import base.EntitySearch
import defines.{EntityType}
import play.Play.application
import rest.{RestPageParams, EntityDAO}
import play.api.libs.iteratee.{Concurrent, Enumerator}
import models.{IsadG,Entity}
import play.api.Logger
import concurrent.Future
import solr.SolrIndexer.{SolrErrorResponse, SolrResponse, SolrUpdateResponse}
import solr.SolrIndexer
import solr.facet.FieldFacetClass
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.Json


object Search extends EntitySearch {

  val searchEntities = List() // i.e. Everything
  val entityFacets = List(
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
  def search = searchAction { page => params => facets => implicit userOpt => implicit request =>
    request match {
      case Accepts.Html() => Ok(views.html.search.search(page, params, facets, routes.Search.search))
      case Accepts.Json() => Ok(Json.toJson(Json.obj(
        "numPages" -> page.numPages,
        "page" -> page.page,
        "items" -> page.items.map(_._1)
        ))
      )
    }
  }

  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   * @param entityType
   * @return
   */
  def filterType(entityType: String) = filterAction(EntityType.withName(entityType)) {
      page => implicit userOpt => implicit request =>
    Ok(Json.obj(
      "numPages" -> page.numPages,
      "page" -> page.page,
      "items" -> page.items.map { case (id, name) =>
        Json.arr(id, name)
      }
    ))
  }


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
    Ok(views.html.search.updateIndex(action=routes.Search.updateIndexPost))
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
          if (firstPageOrErr.isLeft) {
            sys.error(s"Unable to fetch first page of data for $entity: " + firstPageOrErr.left.get)
          }
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
                var rest: List[Future[List[SolrResponse]]] = 2.to(firstPage.numPages.toInt).map { p =>
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
