package eu.ehri.project.search.solr

import play.api.libs.json.JsString
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Configuration, Logger}
import services.ServiceConfig
import services.data.BadJson
import services.search._
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


  private val serviceConfig = ServiceConfig("solr", config)
  private def solrSelectUrl = serviceConfig.baseUrl + "/select"

  private def paramsToForm(seq: Seq[(String, String)]): Map[String, Seq[String]] =
    seq.foldLeft(Map.empty[String,Seq[String]]) { case (m, (key, vals)) =>
      m.updated(key, vals +: m.getOrElse(key, Seq.empty))
    }

  private def fullSearchUrl(query: Seq[(String, String)]) =
    utils.http.joinPath(solrSelectUrl, paramsToForm(query))

  private def dispatch(query: Seq[(String, String)]): Future[WSResponse] = {
    ws.url(solrSelectUrl)
      .post(paramsToForm(query))
      .map { response =>
        if (response.status >= 404) {
          // If we get a server error the server is probably offline or broken for some reason, so
          // we don't want to log thousands of messages until it's fixed. Separate Solr monitoring
          // *should* take care of notification. Note: this includes a 404 error because that's what
          // Solr will give it you attempt to access a core that's been unloaded.
          throw SearchEngineOffline(solrSelectUrl, new SolrServerError(response.status, response.body))
        } else if (response.status >= 400) {
          // If we get a client error it's probably our fault so log a warning and pretend to the
          // user that the server is offline...
          logger.warn(s"Solr client request error: ${response.status}: ${response.body}")
          throw SearchEngineOffline(solrSelectUrl, new SolrServerError(response.status, response.body))
        }
        response
      }
      .recover {
        case e: ConnectException => throw SearchEngineOffline(solrSelectUrl, e)
      }
  }

  override def status(): Future[String] = {
    val host = serviceConfig.hostUrl
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
