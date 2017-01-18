package eu.ehri.project.search.solr

import java.net.ConnectException
import javax.inject.Inject

import backend.rest.BadJson
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Configuration, Logger}
import utils.Page
import utils.search.{SearchHit, _}

import scala.concurrent.Future


/**
  * Class for fetching query results from a Solr instance.
  * Implements the plugin implementation so other search
  * engines/mocks can be substituted.
  */
case class SolrSearchEngine @Inject()(handler: ResponseHandler, ws: WSClient, conf: Configuration) extends SearchEngine {

  private val logger: Logger = Logger(this.getClass)

  private val queryBuilder: SearchQuery => QueryBuilder =
    q => new SolrQueryBuilder(q, WriterType.Json)(conf)

  private lazy val solrPath = utils.serviceBaseUrl("solr", conf)

  private def solrSelectUrl = solrPath + "/select"

  private def fullSearchUrl(query: Map[String, Seq[String]]) = utils.http.joinPath(solrSelectUrl, query)

  private def dispatch(query: Map[String, Seq[String]]): Future[WSResponse] = {
    ws.url(solrSelectUrl)
      .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.FORM)
      .post(query)
      .recover {
        case e: ConnectException => throw SearchEngineOffline(solrSelectUrl, e)
      }
  }

  override def status(): Future[String] = {
    val url = (for {
      host <- conf.getString("services.solr.host")
      port <- conf.getString("services.solr.port")
    } yield s"http://$host:$port/solr/admin/cores")
      .getOrElse(sys.error("Missing config key: service.solr.host/port"))

    val params = Seq("action" -> "STATUS", "wt" -> "json", "core" -> "portal")

    ws.url(url).withQueryString(params: _*).get().map { r =>
      (r.json \ "status" \ "portal" \ "uptime").validate[Int]
        .fold(err => throw BadJson(err), _ => "ok")
    }.recover {
      case e => throw SearchEngineOffline(e.getMessage, e)
    }
  }

  override def filter(query: SearchQuery): Future[SearchResult[FilterHit]] = {
    val queryRequest = queryBuilder(query).simpleFilterQuery()

    logger.trace(fullSearchUrl(queryRequest))
    dispatch(queryRequest).map { response =>
      val parser = handler.getResponseParser(response.body)
      val items = parser.items.map(i => FilterHit(
        i.itemId,
        i.id,
        i.fields.getOrElse(SearchConstants.NAME_EXACT, i.itemId),
        i.`type`,
        i.fields.get(SearchConstants.HOLDER_NAME),
        i.gid
      ))
      val page = Page(query.paging.offset, query.paging.limit, parser.count, items)
      SearchResult(page, query.params, Nil)
    }
  }

  override def search(query: SearchQuery): Future[SearchResult[SearchHit]] = {

    val queryRequest = queryBuilder(query).searchQuery()

    logger.debug(fullSearchUrl(queryRequest))
    dispatch(queryRequest).map { response =>
      val parser = handler.getResponseParser(response.body)
      val facetClassList: Seq[FacetClass[Facet]] = parser.extractFacetData(query.appliedFacets, query.facetClasses)
      val page = Page(query.paging.offset, query.paging.limit, parser.count, parser.items)
      SearchResult(page, query.params, query.appliedFacets, facetClassList, parser.facetInfo, spellcheck = parser.spellcheckSuggestion)
    }
  }
}