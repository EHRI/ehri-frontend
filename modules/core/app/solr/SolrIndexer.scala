package solr

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import models.base._
import play.api.libs.json._
import play.api.libs.json.util._
import models._

import play.Play.application

import defines.EnumUtils.enumWrites
import concurrent.Future
import rest.RestDAO
import play.api.Logger
import defines.EntityType
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import utils.search.{IndexerResponse, Indexer}

case class SolrError(msg: String, code: Int)
object SolrError {
  implicit val solrErrorReads = Json.reads[SolrError]
}

case class SolrHeader(status: Int, time: Int)
object SolrHeader {
  implicit val solrHeaderReads: Reads[SolrHeader] = (
  (__ \ "status").read[Int] and
  (__ \ "QTime").read[Int]
  )(SolrHeader.apply _)
}

sealed trait SolrResponse extends IndexerResponse
case class SolrErrorResponse(err: String) extends SolrResponse
case class SolrUpdateResponse(responseHeader: SolrHeader, error: Option[SolrError] = None) extends SolrResponse
object SolrUpdateResponse {
  implicit val solrUpdateResponseReads: Reads[SolrUpdateResponse] = (
  (__ \ "responseHeader").read[SolrHeader] and
  (__ \ "error").readNullable[SolrError]
  )(SolrUpdateResponse.apply _)
}


object SolrIndexer {

  def dynamicData(js: JsObject): JsObject = Json.toJson(js.value.map { case (k,v) =>
    dynamicFieldName(k, v) -> v
  }.toMap).as[JsObject]

  private def dynamicFieldName(key: String, jsValue: JsValue): String = {
    jsValue match {
      // Ugh, this is temporary hopefully...
      case s: JsString if s == "languageCode" => key
      case v: JsArray => key + "_ss" // Multivalue string
      case v: JsNumber => key + "_i"  // integer ???
      case v: JsString => key + "_t"  // Text general
      case v: JsBoolean => key + "_b" // Boolean
      case _ => key
    }
  }
}

/**
 * Object containing functions for managing the Solr index.
 * Most of this should eventually go away when we have a
 * proper external indexing service.
 */
case class SolrIndexer(typeRegistry: Map[EntityType.Value, JsObject => Seq[JsObject]]) extends Indexer with RestDAO {

  import SolrConstants._
  import SolrIndexer._

  implicit val lang: Lang = Lang.defaultLang

  // We don't need a user here yet unless we want to log
  // when the Solr index is changed.
  val userProfile: Option[UserProfile] = None
  private def updateUrl(commit: Boolean = true) = {
    "%s/update?wt=json&commit=%s&optimize=%s".format(application.configuration.getString("solr.path"), commit, commit)
  }
  private val batchSize = application.configuration.getInt("solr.update.batchSize")

  override val headers = Map(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json; charset=utf8"
  )

  /**
   * Possible responses from Solr calls.
   */

  /**
   * Commit all pending docs
   */
  def commit: Future[SolrResponse] = solrUpdate(Json.obj(), commit = true)

  /**
   * Delete a list of Solr models.
   */
  def deleteItemsById(items: Stream[String], commit: Boolean = true): Future[SolrResponse] = {
    // NB: Solr delete syntax requires a MAP rather than a list
    val deleteJson = items.foldLeft(Json.obj()) { case (obj,id) =>
      // NB: Because we delete logical items, but descriptions are indexed
      // we use a slightly dodgy query to delete stuff...
      obj + ("delete" -> Json.obj("query" -> "id:\"%s\" OR %s:\"%s\"".format(id, ITEM_ID, id)))
    }
    solrUpdate(deleteJson, commit = commit)
  }

  /*
   * Delete a list of Solr models.
   */
  def deleteItems(items: Stream[AnyModel], commit: Boolean = true): Future[SolrResponse] = {
    deleteItemsById(items.map(_.id), commit = commit)
  }

  def deleteItemsByType(entityType: EntityType.Value, commit: Boolean = true): Future[SolrResponse] = {
    val deleteJson = Json.obj(
      "delete" -> Json.obj("query" -> (TYPE + ":" + entityType.toString))
    )
    solrUpdate(deleteJson, commit = commit)
  }

  /**
   * Delete everything in the index. USE WITH CAUTION!!!
   * @return
   */
  def deleteAll(commit: Boolean = false): Future[SolrResponse] = {
    val deleteJson = Json.obj(
      "delete" -> Json.obj("query" -> ("id:*"))
    )
    solrUpdate(deleteJson, commit = commit)
  }

  /**
   * Update a single item
   */
  def updateItem(item: JsObject, commit: Boolean = true): Future[SolrResponse] = {
    solrUpdate(Json.toJson(itemToJson(item)), commit = commit)
  }

  /*
   * Update a list of Solr models. The actual list is broken up
   * into batches of a fixed size so this function can accept
   * arbitrarily long lists.
   */
  def updateItems(items: Stream[JsObject], commit: Boolean = true): Future[List[SolrResponse]] = {
    Future.sequence(items.grouped(batchSize).toList.map { batch =>
      try {
        solrUpdate(Json.toJson(batch.toList.flatMap(itemToJson)), commit = commit)
      } catch {
        case e: Throwable => {
          Logger.logger.error("Caught error running solrUpdate: " + e.getMessage, e)
          throw e
        }
      }
    })
  }

  /**
   * Run a Solr command, consisting of some Json.
   * @param updateJson
   * @return
   */
  private def solrUpdate(updateJson: JsValue, commit: Boolean = true): Future[SolrResponse] = {
    WS.url(updateUrl(commit)).withHeaders(headers.toList: _*)
        .post(updateJson).map { response =>
        Logger.logger.debug("Solr response: " + response.body)
      response.json.validate[SolrUpdateResponse].fold({ err =>
          Logger.error("Solr update error response: " + err)
          SolrErrorResponse(response.body)
        }, { sur => sur }
        )
    }
  }

  /**
   * Very generic fallback Solr converter for types that haven't
   * registered their own converter.
   */
  val genericConversion: JsObject => Seq[JsObject] = { item =>
    val data = dynamicData((item \ Entity.DATA).as[JsObject])
    Seq(data ++ Json.obj(
      ID -> (item \ ID),
      ITEM_ID -> (item \ ID),
      TYPE -> (item \ TYPE),
      "identifier" -> (item \ "identifier"),
      NAME_EXACT -> (item \ NAME_EXACT),
      LAST_MODIFIED -> (item \ Entity.RELATIONSHIPS \ Ontology.IS_ACCESSIBLE_TO)
        .asOpt[List[JsObject]].flatMap(_.headOption.map(o => (o \ Entity.DATA \ Ontology.EVENT_TIMESTAMP))),
      ACCESSOR_FIELD -> (item \ Entity.RELATIONSHIPS \ Ontology.IS_ACCESSIBLE_TO)
              .asOpt[List[JsObject]].getOrElse(Nil).map(o => (o \ ID))
    ))
  }

  /**
   * Convert a given entity to the appropriate JSON for solr update.
   * @param item
   * @return
   */
  private def itemToJson(item: JsObject): Seq[JsObject] = {
    item.value.get(Entity.TYPE).map(_.as[String]).map { et =>
      typeRegistry.get(EntityType.withName(et)).map { func =>
        func(item)
      }.getOrElse {
        genericConversion(item)
      }
    }.getOrElse(Nil)
  }
}
