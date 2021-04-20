package eu.ehri.project.search.solr

import config.serviceHost
import config.serviceBaseUrl
import play.api.libs.json.JsString
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Configuration, Logger}
import services.data.BadJson
import services.search.{SearchHit, _}
import utils.Page

import java.net.ConnectException
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


/**
  * Class for fetching query results from a Solr instance.
  * Implements the plugin implementation so other search
  * engines/mocks can be substituted.
  */
case class SolrSearchEngine @Inject()(
  queryBuilder: QueryBuilder,
  parser: ResponseParser,
  ws: WSClient,
  queryLog: SearchLogger,
  config: Configuration
)(implicit executionContext: ExecutionContext) extends SearchEngine {

  private val logger: Logger = Logger(this.getClass)

  private lazy val solrPath = serviceBaseUrl("solr", config)

  private def solrSelectUrl = solrPath + "/select"

  private def paramsToForm(seq: Seq[(String, String)]): Map[String, Seq[String]] =
    seq.foldLeft(Map.empty[String,Seq[String]]) { case (m, (key, vals)) =>
      m.updated(key, vals +: m.getOrElse(key, Seq.empty))
    }

  private def fullSearchUrl(query: Seq[(String, String)]) =
    utils.http.joinPath(solrSelectUrl, paramsToForm(query))

  private def dispatch(query: Seq[(String, String)]): Future[WSResponse] = {
    ws.url(solrSelectUrl)
      .post(paramsToForm(query))
      .recover {
        case e: ConnectException => throw SearchEngineOffline(solrSelectUrl, e)
      }
  }

  override def status(): Future[String] = {
    val host = serviceHost("solr", config)
    val url = s"$host/solr/admin/cores"
    val params = Seq("action" -> "STATUS", "wt" -> "json", "core" -> "portal")

    ws.url(url).withQueryStringParameters(params: _*).get().map { r =>
      (r.json \ "status" \ "portal" \ "uptime").validate[Long]
        .fold(err => throw BadJson(err), _ => "ok")
    }.recover {
      case e => throw SearchEngineOffline(e.getMessage, e)
    }
  }

  override def filter(query: SearchQuery): Future[SearchResult[FilterHit]] = {
    val queryRequest = queryBuilder.simpleFilterQuery(query)
    logger.trace(fullSearchUrl(queryRequest))

    dispatch(queryRequest).map { response =>
      val data = parser.parse(response.body, query.facetClasses, query.appliedFacets)
      val items = data.items.map(i => FilterHit(
        i.itemId,
        i.id,
        i.fields.collectFirst { case (SearchConstants.NAME_EXACT, JsString(id)) => id }.getOrElse(i.itemId),
        i.`type`,
        i.fields.collectFirst { case (SearchConstants.HOLDER_NAME, JsString(id)) => id },
        i.gid
      ))
      val page = Page(query.paging.offset, query.paging.limit, data.count, items)
      SearchResult(page, query.params, Nil)
    }
  }

  override def search(query: SearchQuery): Future[SearchResult[SearchHit]] = {
    val queryRequest = queryBuilder.searchQuery(query)
    logger.debug(fullSearchUrl(queryRequest))
    queryLog.log(ParamLog(query.params, query.appliedFacets, query.facetClasses, query.filters))

    // Skip dispatch if we have an empty ID set
    query.withinIds match {
      case Some(ids) if ids.isEmpty =>
        logger.debug("Empty ID filter set so returning empty search result")
        Future.successful(SearchResult(Page.empty[SearchHit], query.params, query.appliedFacets))
      case _ => dispatch(queryRequest).map { response =>
        val data = parser.parse(response.body, query.facetClasses, query.appliedFacets)
        val page = Page(query.paging.offset, query.paging.limit, data.count, data.items)
        SearchResult(page, query.params, query.appliedFacets, data.facets, data.facetInfo, spellcheck = data.spellcheckSuggestion)
      }
    }
  }
}
