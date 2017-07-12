package controllers.base

import backend.rest.cypher.Cypher
import controllers.generic.Search
import models.VirtualUnit
import models.base.AnyModel
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.mvc.RequestHeader
import utils.search.SearchConstants._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


/**
 * Helpers for searching virtual collections
 */
trait SearchVC {
  this: Search =>

  protected implicit def cache: SyncCacheApi
  protected def cypher: Cypher

  private def logger = Logger(getClass)

  /**
    * Filters for searching a virtual collection, depending on
    * whether or not there is a current query. If no query is
    * given the filters will fetch items at the child level only.
    * Otherwise they will return filters for items at all depths
    * in the virtual collection.
    */
  protected def vcSearchFilters(item: AnyModel)(implicit request: RequestHeader): Future[Map[String,Any]] =
    if (!hasActiveQuery(request)) buildChildSearchFilter(item)
    else buildDescendentSearchFilter(item)

  /**
   * Fetch a list of descendant IDs for a given virtual collection
   * in order to constrain a search space. This is:
   *  - child virtual collections
   *  - top level documentary units
   *
   * @param id the parent VC id
   * @return a sequence of descendant IDs
   */
  protected def vcDescendantIds(id: String): Future[Seq[String]] = {
    import play.api.libs.json._

    val reader: Reads[Seq[String]] =
      (__ \ "data").read[Seq[Seq[Seq[String]]]]
        .map { r => r.flatten.flatten }

    cypher.get[Seq[String]](
      """
        |MATCH (vc:VirtualUnit {__id: {vcid}})
        |OPTIONAL MATCH (vc)<-[:isPartOf*]-(child)
        |OPTIONAL MATCH (ddoc)<-[:includesUnit]-(vc)
        |OPTIONAL MATCH (doc)<-[:includesUnit]-(child)
        |RETURN DISTINCT collect(DISTINCT child.__id) + collect(DISTINCT doc.__id) + collect(DISTINCT ddoc.__id)
      """.stripMargin, Map("vcid" -> play.api.libs.json.JsString(id)))(reader).map { seq =>
      logger.debug(s"Elements: ${seq.length}, distinct: ${seq.distinct.length}")

      config.getOptional[Int]("search.vc.maxDescendants").map { vcLimit =>
        if (seq.length > vcLimit) {
          logger.error(s"Truncating clauses on child item search for $id: items ${seq.length}")
          seq.distinct.take(vcLimit)
        } else seq
      }.getOrElse(seq)
    }
  }

  /**
   * Fetch a list of descendant IDs for a given virtual collection
   * in order to constrain a search space. This is:
   *  - child virtual collections
   *  - top level documentary units
   *
   * @param item a documentary unit or virtual collection
   * @return a sequence of descendant IDs
   */
  protected def descendantIds(item: AnyModel): Future[Seq[String]] = {
    item match {
      case v: VirtualUnit => vcDescendantIds(item.id)
      case d => Future.successful(Seq(item.id))
    }
  }

  protected def buildDescendentSearchFilter(item: AnyModel): Future[Map[String, Any]] =
    item match {
      case v: VirtualUnit => vcDescendantIds(item.id).map { seq =>
        if (seq.isEmpty) Map(ITEM_ID -> "__NO_VALID_ID__")
        else Map(s"$ITEM_ID:(${seq.mkString(" ")}) OR $ANCESTOR_IDS:(${seq.mkString(" ")})" -> Unit)
      }
      // otherwise, for documentary units, we can just query
      // all ancestors
      case d => immediate(Map(s"$ANCESTOR_IDS:${item.id}" -> Unit))
    }

  protected def buildChildSearchFilter(item: AnyModel): Future[Map[String,Any]] =
    immediate {
      // Nastiness. We want a Solr query that will allow searching
      // both the child virtual collections of a VU as well as the
      // physical documentary units it includes. Since there is no
      // connection from the DU to VUs it belongs to (and creating
      // one is not feasible) we need to do this badness:
      // - load the VU from the graph along with its included DUs
      // - query for anything that has the VUs parent ID *or* anything
      // with an itemId among its included DUs
      import utils.search.SearchConstants._
      logger.debug(s"Building child search for: ${item.id}")
      item match {
        case v: VirtualUnit =>
          val pq = v.includedUnits.map(_.id)
          if (pq.isEmpty) Map(s"$PARENT_ID:${v.id}" -> Unit)
          else Map(s"$PARENT_ID:${v.id} OR $ITEM_ID:(${pq.mkString(" ")})" -> Unit)
        case d => Map(s"$PARENT_ID:${d.id}" -> Unit)
      }
    }
}
