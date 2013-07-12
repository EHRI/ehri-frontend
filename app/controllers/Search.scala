package controllers

import play.api.mvc._
import base.EntitySearch
import defines.EntityType
import play.Play.application
import rest.{RestPageParams, EntityDAO}
import play.api.libs.iteratee.{Concurrent, Enumerator}
import models.IsadG
import play.api.Logger
import concurrent.Future
import solr.SolrIndexer._
import solr.{SolrIndexer}
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.{JsObject, Writes, Json}
import solr.SolrIndexer.SolrHeader
import solr.facet.FieldFacetClass
import solr.SolrIndexer.SolrUpdateResponse
import solr.SolrIndexer.SolrErrorResponse
import models.base.AnyModel
import utils.search.{SearchOrder, SearchParams}


object Search extends EntitySearch {

  val searchEntities = List() // i.e. Everything
  override val entityFacets = List(
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
      defaultParams = Some(SearchParams(sort = Some(SearchOrder.Score)))) {
      page => params => facets => implicit userOpt => implicit request =>
    render {
      case Accepts.Json() => {
        Ok(Json.toJson(Json.obj(
          "numPages" -> page.numPages,
          "page" -> Json.toJson(page.items.map(_._1))(Writes.seq(AnyModel.Converter.clientFormat)),
          "facets" -> facets
        ))
        )
      }
      case _ => Ok(views.html.search.search(page, params, facets, routes.Search.search))
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


  /**
   * Message that terminates a long-lived streaming response, such
   * as the search index update job.
   */
  val DONE_MESSAGE = "Done"


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
    Ok(views.html.search.updateIndex(form = updateIndexForm, action=routes.Search.updateIndexPost))
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

    // Override default execution context
    implicit val executionContext = solr.Contexts.searchIndexExecutionContext

    /**
     * Clear everything from the index...
     */
    def optionallyClearIndex(doit: Boolean): Future[Option[SolrResponse]] = {
      if (!doit) Future.successful(None)
      else solr.SolrIndexer.deleteAll().map(r => Some(r))
    }

    /**
     * Update a single page of data
     */
    def updatePage(entityType: EntityType.Value, params: RestPageParams, list: List[JsObject], chan: Concurrent.Channel[String]
                    ): Future[List[SolrResponse]] = {
      solr.SolrIndexer.updateItems(list.toStream, commit = false).map { jobs =>
        jobs.map { response =>
          response match {
            case r@SolrUpdateResponse(SolrHeader(code, time), Some(SolrError(msg, _))) => {
              Logger.logger.error(msg)
              chan.push(wrapMsg(msg))
              r
            }
            case r@SolrUpdateResponse(SolrHeader(code, time), None) => {
              val msg = s"Batch complete: $entityType (${params.range}, time: ${time})"
              Logger.logger.info(msg)
              chan.push(wrapMsg(msg))
              r
            }
            case e: SolrErrorResponse => {
              Logger.logger.error(s"Unable to page page data for entity: $entityType, page: ${list}: {}", e.err)
              e
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
      val params = RestPageParams(page=Some(pageNum), limit = Some(batchSize))
      val getData = EntityDAO[AnyModel](entityType, userOpt).listJson(params)
      getData.flatMap { listOrErr =>
        listOrErr match {
          case Left(err) => {
            Logger.logger.error(s"Unable to page page data for entity: $entityType, page: ${pageNum}: {}", err)
            Future.successful(List(SolrErrorResponse(listOrErr.left.get.getMessage)))
          }
          case Right(page) => updatePage(entityType, params, page, chan)
        }
      }
    }

    // Create an unicast channel in which to feed progress messages
    val channel = Concurrent.unicast[String] { chan =>
      optionallyClearIndex(deleteAll).flatMap { maybeResponse =>
        maybeResponse.map { r =>
          chan.push(wrapMsg(s"deleted index: $r"))
        }

        // Now get on with the real work...
        chan.push(wrapMsg(s"Initiating update for entities: ${entities.mkString(", ")}"))
        var all: List[Future[List[SolrResponse]]] = entities.map { entity =>
          EntityDAO(entity, userOpt).count().flatMap { countOrErr =>
            if (countOrErr.isLeft) {
              Logger.logger.error("Unable to fetch first page of data for $entity: " + countOrErr.left.get)
              sys.error(s"Unable to fetch first page of data for $entity: " + countOrErr.left.get)
            }
            val count = countOrErr.right.get
            chan.push(wrapMsg(s"Indexing: $entity (total count: $count)"))

            val pageCount = (count / batchSize) + (count % batchSize).min(1)

            // Clear all Entities from the index...
            var allUpdateResponses: Future[List[SolrResponse]] = solr.SolrIndexer.deleteItemsByType(entity, commit = false).flatMap { response =>
              response match {
                case e: SolrErrorResponse => {
                  chan.push(wrapMsg(s"Error deleting items for entity: $entity: ${e.err}"))
                  Future.successful(List(response))
                }
                case ok => {
                  // Run each page in sequence...
                  var pages: List[Future[List[SolrResponse]]] = 1L.to(pageCount).map { p =>
                    val page = updateItemSet(entity, p.toInt, chan)
                    page onFailure {
                      case e => chan.push(wrapMsg(e.getMessage))
                    }
                    page
                  }.toList

                  // Flatten the inner batch results into a single list
                  val all = Future.sequence(pages).map(l => l.flatMap(i => i))
                  all onFailure {
                    case e => chan.push(wrapMsg(e.getMessage))
                  }
                  all
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
              case SolrUpdateResponse(SolrHeader(code, time), None) => {
                total + time
              }
              case SolrUpdateResponse(SolrHeader(code, time), Some(SolrError(msg, _))) => {
                total
              }
              case e => total
            }
          }
          chan.push(wrapMsg("Completed indexing in: " + totaltime + " - committing..."))
          SolrIndexer.commit.map { resOrErr =>
            resOrErr match {
              case e: SolrErrorResponse => {
                chan.push(wrapMsg("Error committing Solr data: " + e.err))
              }
              case ok: SolrUpdateResponse => {
                Logger.logger.info("Committing...")
                chan.push(wrapMsg("Committed in " + ok.responseHeader.time))
              }
            }
            chan.push(wrapMsg(DONE_MESSAGE))
            chan.eofAndEnd()
          }
        }
      }
    }

    Ok.stream(channel.andThen(Enumerator.eof))
  }
}
