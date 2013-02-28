package solr

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{Response, WS}
import models.base.{DescribedEntity, AccessibleEntity}
import play.api.libs.json._
import models._

import play.Play.application

import defines.EnumWriter.enumWrites
import concurrent.Future
import rest.{ServerError, ValidationError, RestDAO, RestError}
import play.api.Logger
import models.Repository
import play.api.libs.json.JsObject
import models.DocumentaryUnit
import defines.EntityType


object SolrIndexer extends RestDAO {
  // We don't need a user here yet unless we want to log
  // when the Solr index is changed.
  val userProfile: Option[UserProfile] = None
  private def updateUrl = "%s/update?wt=json&commit=true".format(application.configuration.getString("solr.path"))
  private val batchSize = application.configuration.getInt("solr.update.batchSize")

  override val headers = Map(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json; charset=utf8"
  )

  case class SolrUpdateResponse(status: Int, time: Int)

  import play.api.libs.functional.syntax._
  implicit val solrUpdateResponseReads: Reads[SolrUpdateResponse] = (
    (__ \ "responseHeader" \ "status").read[Int] and
    (__ \ "responseHeader"\ "QTime").read[Int]
  )(SolrUpdateResponse.apply _)

  /*
   * Delete a list of Solr models.
   */
  def deleteItemsById(items: Stream[String]): Future[Either[RestError,SolrUpdateResponse]] = {
    // NB: Solr delete syntax requires a MAP rather than a list
    val delete = items.foldLeft(Json.obj()) { case (obj,id) =>
      // NB: Because we delete logical items, but descriptions are indexed
      // we use a slightly dodgy query to delete stuff...
      //Json.obj("delete" -> Json.obj("query" -> s"id:'$id' OR itemId:'$id'"))
      obj + ("delete" -> Json.obj("query" -> s"id:'$id' OR itemId:'$id'"))
    }

    WS.url(updateUrl).withHeaders(headers.toList: _*).post(delete).map { response =>
      checkError(response).right.map(_.json.validate[SolrUpdateResponse].fold({ err =>
        Logger.logger.error("Unexpected Solr delete response: {}", response.body)
        sys.error(err.toString)
      }, { sur =>
        println(sur)
        sur
      }
      ))
    }
  }

  /*
   * Delete a list of Solr models.
   */
  def deleteItems(items: Stream[Entity]): Future[Either[RestError,SolrUpdateResponse]] = {
    deleteItemsById(items.map(_.id))
  }

  /*
   * Update a list of Solr models. The actual list is broken up
   * into batches of a fixed size so this function can accept
   * arbitrarily long lists.
   */
  def updateItems(items: Stream[Entity]): Future[List[Either[RestError,SolrUpdateResponse]]] = {
    Future.sequence(items.grouped(batchSize).toList.map(stream => updateBatch(stream.toList)))
  }

  /* Update a single batch of solr models.
   */
  private def updateBatch(items: List[Entity]): Future[Either[RestError,SolrUpdateResponse]] = {
    val data = items.flatMap(itemToJson)
    WS.url(updateUrl).withHeaders(headers.toList: _*).post(Json.toJson(data)).map { response =>
      checkError(response).right.map(_.json.validate[SolrUpdateResponse].fold({ err =>
          Logger.logger.error("Unexpected Solr update response: {}", response.body)
          sys.error(err.toString)
        }, { sur =>
        sur
      }
      ))
    }
  }

  def itemToJson(item: Entity): List[JsObject] = item.isA match {
    case EntityType.DocumentaryUnit => docToSolr(DocumentaryUnit(item))
    case EntityType.Agent => repoToSolr(Repository(item))
    case EntityType.Concept => conceptToSolr(Concept(item))
    case any => entityToSolr(item)
  }
  
  private def docToSolr(d: DocumentaryUnit): List[JsObject] = {
    val descriptions = describedEntityToSolr(d)
    descriptions.map { desc =>
      ((desc
        + ("holderId" -> Json.toJson(d.holder.map(_.id))))
        + ("holderName" -> Json.toJson(d.holder.map(_.name))))
    }
  }
  
  private def repoToSolr(d: Repository): List[JsObject] = {
    describedEntityToSolr(d)
  }

  /**
   * Convert a Concept to JSON
   * @param c
   * @return
   */
  private def conceptToSolr(c: Concept): List[JsObject] = {
    val descriptions = describedEntityToSolr(c)
    descriptions.map { desc =>
      ((desc
        + ("vocabularyId_s" -> Json.toJson(c.vocabulary.map(_.id))))
        + ("vocabularyName_s" -> Json.toJson(c.vocabulary.map(_.name))))
    }
  }

  /**
   * Convert a DescribedEntity into one flat Solr representation
   * per description.
   * @param d
   * @return
   */
  private def describedEntityToSolr(d: DescribedEntity): List[JsObject] = {
    d.descriptions.map { desc =>
      val baseData = Json.obj(
        "itemId" -> d.id,
        "id" -> desc.id,
        "type" -> desc.e.isA,
        "accessibleTo" -> d.accessors.map(a => a.id)
      )
      // Merge in all the additional data already in the entity
      // Don't overwrite keys added specifically
      desc.e.data.toSeq.foldLeft(baseData) { case (bd, (key, jsval)) =>
        if (!bd.keys.contains(key))
          bd + (key, jsval)
        else
          bd
      }
    }
  }

  /**
   * Convert an AccessibleEntity to a Solr representation.
   * @param d
   * @return
   */
  private def entityToSolr(d: Entity): List[JsObject] = {
    val baseData = Json.obj(
      "id" -> d.id,
      "type" -> d.isA,
      "accessibleTo" -> d.relations(AccessibleEntity.ACCESS_REL).map(a => a.id)
    )
    // Merge in all the additional data already in the entity
    // Don't overwrite keys added specifically
    val full = d.data.toSeq.foldLeft(baseData) { case (bd, (key, jsval)) =>
      if (!bd.keys.contains(key))
        bd + (key, jsval)
      else
        bd
    }
    List(full)
  }
}