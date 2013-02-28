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


object SearchAdmin extends Controller with AuthController with ControllerHelpers {


  def updateIndex = adminAction { implicit userOpt => implicit request =>
    Ok(views.html.updateIndex(action=routes.SearchAdmin.updateIndexPost))
  }

  def updateIndexPost = adminAction { implicit userOpt => implicit request =>

    val batchSize = application.configuration.getInt("solr.update.batchSize")

    import play.api.data.Form
    import play.api.data.Forms._
    import models.forms.enum

    val entities = Form(single("type" -> list(enum(defines.EntityType)))).bindFromRequest.value.get

    def updatePage(entityType: EntityType.Value, page: rest.Page[Entity], chan: Concurrent.Channel[String]) = {
      solr.SolrIndexer.updateItems(page.items.toStream).map { jobs =>
        println(jobs)
        jobs.map { doneOrErr =>
          doneOrErr.right.map { solrResponse =>
            val msg = s"Done Solr update batch: $entityType (page: ${page.page}, time: ${solrResponse.time})"
            println(msg)
            Logger.logger.info(msg)
            chan.push(msg + "\n")
          }
        }
      }
    }

    def updateItemSet(entityType: EntityType.Value, pageNum: Int, chan: Concurrent.Channel[String]): Unit = {
      EntityDAO(entityType, userOpt).page(RestPageParams(page=Some(pageNum))).map { pageOrErr =>
        pageOrErr.right.map(page => updatePage(entityType, page, chan))
      }
    }

    val channel = Concurrent.unicast[String] { chan =>
      for {
        entity <- entities
        pageOrErr <- EntityDAO(entity, userOpt).page(RestPageParams())
      } {
        // Clear all Entities from the index...
        solr.SolrIndexer.deleteItemsByType(entity).map { resOrErr =>
          resOrErr.right.map { _ =>
            for { page <- pageOrErr.right } {
              // Since we've already fetched a page of data, reuse it immediately
              updateItemSet(entity, 1, chan)
              2.to(page.numPages.toInt).foreach { p =>
                updateItemSet(entity, p.toInt, chan)
              }
            }
          }
        }
      }
    }

    Ok.stream(channel.andThen(Enumerator.eof))
  }

}