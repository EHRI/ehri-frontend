package eu.ehri.project.search.solr

import java.net.ConnectException
import javax.inject.Inject

import backend.rest.BadJson
import play.api.libs.json.JsString
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Configuration, Logger}
import utils.Page
import utils.search.{SearchHit, _}

import scala.concurrent.{ExecutionContext, Future}


/**
  * Class for fetching query results from a Solr instance.
  * Implements the plugin implementation so other search
  * engines/mocks can be substituted.
  */
case class SolrSearchEngine @Inject()(
  queryBuilder: QueryBuilder,
  parser: ResponseParser,
  ws: WSClient, config: Configuration)(implicit executionContext: ExecutionContext)
  extends SearchEngine {

  private val logger: Logger = Logger(this.getClass)

  private lazy val solrPath = utils.serviceBaseUrl("solr", config)

  private def solrSelectUrl = solrPath + "/select"

  private def fullSearchUrl(query: Seq[(String, String)]) =
    utils.http.joinPath(solrSelectUrl, utils.http.paramsToForm(query))

  private def dispatch(query: Seq[(String, String)]): Future[WSResponse] = {
    ws.url(solrSelectUrl)
      .post(utils.http.paramsToForm(query))
      .recover {
        case e: ConnectException => throw SearchEngineOffline(solrSelectUrl, e)
      }
  }

  override def status(): Future[String] = {
    val url = (for {
      host <- config.getOptional[String]("services.solr.host")
      port <- config.getOptional[String]("services.solr.port")
    } yield s"http://$host:$port/solr/admin/cores")
      .getOrElse(sys.error("Missing config key: service.solr.host/port"))

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
        i.fields
          .collect { case (SearchConstants.NAME_EXACT, JsString(id)) => id}
          .headOption.getOrElse(i.itemId),
        i.`type`,
        i.fields
          .collect { case (SearchConstants.HOLDER_NAME, JsString(id)) => id}
          .headOption,
        i.gid
      ))
      val page = Page(query.paging.offset, query.paging.limit, data.count, items)
      SearchResult(page, query.params, Nil)
    }
  }

  override def search(query: SearchQuery): Future[SearchResult[SearchHit]] = {
    val queryRequest = queryBuilder.searchQuery(query)
    logger.debug(fullSearchUrl(queryRequest))

    dispatch(queryRequest).map { response =>
      val data = parser.parse(response.body, query.facetClasses, query.appliedFacets)
      val page = Page(query.paging.offset, query.paging.limit, data.count, data.items)
      SearchResult(page, query.params, query.appliedFacets, data.facets, data.facetInfo, spellcheck = data.spellcheckSuggestion)
    }
  }
}
