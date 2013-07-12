package solr

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import models.base._
import play.api.libs.json._
import models._

import play.Play.application

import defines.EnumUtils.enumWrites
import concurrent.Future
import rest.RestDAO
import play.api.Logger
import defines.EntityType
import play.api.i18n.Lang
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject

/**
 * Object containing functions for managing the Solr index.
 * Most of this should eventually go away when we have a
 * proper external indexing service.
 */
object SolrIndexer extends RestDAO {

  import SolrConstants._

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

  case class SolrError(msg: String, code: Int)
  implicit val solrErrorReads = Json.reads[SolrError]

  case class SolrHeader(status: Int, time: Int)
  import play.api.libs.functional.syntax._
  implicit val solrHeaderReads: Reads[SolrHeader] = (
    (__ \ "status").read[Int] and
    (__ \ "QTime").read[Int]
  )(SolrHeader.apply _)

  sealed trait SolrResponse
  case class SolrErrorResponse(err: String) extends SolrResponse
  case class SolrUpdateResponse(responseHeader: SolrHeader, error: Option[SolrError] = None) extends SolrResponse
  implicit val solrUpdateResponseReads: Reads[SolrUpdateResponse] = (
    (__ \ "responseHeader").read[SolrHeader] and
    (__ \ "error").readNullable[SolrError]
  )(SolrUpdateResponse.apply _)

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
          Logger.logger.error(e.getMessage, e)
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
      response.json.validate[SolrUpdateResponse].fold({ err =>
          SolrErrorResponse(response.body)
        }, { sur => sur }
        )
    }
  }

  /**
   * Convert a given entity to the appropriate JSON for solr update.
   * @param item
   * @return
   */
  private def itemToJson(item: JsObject): List[JsObject] = {
    item.validate[AnyModel](AnyModel.Converter.restReads).fold(
      invalid = { err =>
        Logger.logger.error("Bad JSON on reindex: " + JsError.toFlatJson(err))
        Nil
      },
      valid = { any =>
        any match {
          case d: DocumentaryUnit => docToSolr(d, item)
          case d: HistoricalAgent => actorToSolr(d, item)
          case d: Repository => repoToSolr(d, item)
          case d: Concept => conceptToSolr(d, item)
          case d: Accessible => anyToSolr(d, item)
          case d => {
            Logger.logger.error(s"Unexpected non-accessible item recieved for indexing: ${d.isA}: ${d.id}}")
            Nil
          }
        }
      }
    )
  }

  private def docToSolr(d: DocumentaryUnit, json: JsObject): List[JsObject] = {
    val descriptions = describedEntityToSolr[DocumentaryUnitDescriptionF,DocumentaryUnitF,DocumentaryUnit](d, json)
    descriptions.map { desc =>
      ((desc
        + ("copyrightStatus" -> Json.toJson(d.model.copyrightStatus))
        + ("scope" -> Json.toJson(d.model.scope))
        + ("parentId" -> Json.toJson(d.parent.map(_.id)))
        + ("depthOfDescription" -> Json.toJson(d.ancestors.length))
        + ("holderId" -> Json.toJson(d.holder.map(_.id))))
        + ("holderName" -> Json.toJson(d.holder.map(_.toStringLang))))
    }
  }

  private def actorToSolr(d: HistoricalAgent, json: JsObject): List[JsObject] = {
    val descriptions = describedEntityToSolr[HistoricalAgentDescriptionF,HistoricalAgentF,HistoricalAgent](d, json)
    // FIXME: This is very stupid
    descriptions.zipWithIndex.map { case (desc,i) =>
      (desc
        + ("holderId" -> Json.toJson(d.set.map(_.id)))
        + ("holderName" -> Json.toJson(d.set.map(_.toStringLang)))
        + (Isaar.ENTITY_TYPE -> Json.toJson(d.model.descriptions(i).entityType)))
    }
  }

  private def repoToSolr(d: Repository, json: JsObject): List[JsObject] = {
    val descriptions = describedEntityToSolr[RepositoryDescriptionF,RepositoryF,Repository](d, json)
    descriptions.map { desc =>
      ((desc
        + ("addresses" -> Json.toJson(d.model.descriptions.flatMap(_.addresses.map(_.toString)).distinct))
        + (OTHER_NAMES -> Json.toJson(d.descriptions.flatMap(_.otherFormsOfName).distinct))
        + (PARALLEL_NAMES -> Json.toJson(d.descriptions.flatMap(_.parallelFormsOfName).distinct))
        + ("countryCode" -> Json.toJson(d.country.map(_.id)))
        + ("priority" -> Json.toJson(d.model.priority))))
    }
  }

  /**
   * Convert a Concept to JSON
   * @param d
   * @return
   */
  private def conceptToSolr(d: Concept, json: JsObject): List[JsObject] = {
    val descriptions = describedEntityToSolr[ConceptDescriptionF,ConceptF,Concept](d, json)
    descriptions.map { desc =>
      ((desc
        + ("parentId" -> Json.toJson(d.broaderTerms.map(_.id)))
        + ("holderId" -> Json.toJson(d.vocabulary.map(_.id))))
        + ("holderName" -> Json.toJson(d.vocabulary.map(_.toStringLang))))
    }
  }

  /**
   * Convert a DescribedEntity into one flat Solr representation
   * per description.
   * @param json
   * @return
   */
  private def describedEntityToSolr[D<:Description,T<:Described[D], TM<:DescribedMeta[D,T] with Accessible](
               d: TM, json: JsObject): List[JsObject] = {

    val base = d.descriptions.map { desc =>
      Json.obj(
        ITEM_ID -> d.id,
        Entity.TYPE -> d.isA,
        Entity.ID -> desc.id,
        "languageCode" -> desc.languageCode,
        ACCESSOR_FIELD -> d.accessors.map(_.id),
        "lastUpdated" -> d.latestEvent.map(_.model.datetime),
        NAME_EXACT -> d.toStringLang
      )
    }

    (json \ Entity.RELATIONSHIPS \ Described.REL).validate[List[JsObject]].fold(
      invalid = { err =>
        Logger.logger.error("Unable to extract descriptions: " + JsError.toFlatJson(err))
        Nil
      },
      valid = { list =>
        list.zipWithIndex.map { case (jsobj, i) =>
          Json.toJson((jsobj \ Entity.DATA).as[JsObject].fields.map { case (key, jsval) =>
            dynamicFieldName(key, jsval) -> jsval
          }.toMap).as[JsObject].deepMerge(base(i))
        }
      }
    )
  }

  /**
   * Convert an AccessibleEntity to a Solr representation.
   * @param d
   * @return
   */
  private def anyToSolr(d: Accessible, json: JsObject): List[JsObject] = {
    val baseData = Json.obj(
      "id" -> d.id,
      ITEM_ID -> d.id, // Duplicate, because the 'description' IS the item.
      TYPE -> d.isA,
      NAME_EXACT -> d.toStringLang,
      ACCESSOR_FIELD -> d.accessors.map(_.id),
      LAST_MODIFIED -> d.latestEvent.map(_.model.datetime)
    )
    // Merge in all the additional data already in the entity
    // Don't overwrite keys added specifically
    val full = (json \ Entity.DATA).as[JsObject].value.foldLeft(baseData) { case (bd, (key, jsval)) =>
      if (!bd.keys.contains(key))
        bd + (dynamicFieldName(key, jsval) -> jsval)
      else
        bd
    }
    List(full)
  }

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
