package controllers.portal

import auth.AccountManager
import backend.Backend
import backend.rest.cypher.Cypher
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.HistoricalAgent
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import utils.MovedPageLookup
import utils.search._
import views.MarkdownRenderer

@Singleton
case class HistoricalAgents @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends PortalController
  with Generic[HistoricalAgent]
  with Search
  with FacetConfig {

  private val portalAgentRoutes = controllers.portal.routes.HistoricalAgents

  def searchAll = UserBrowseAction.async { implicit request =>
    findType[HistoricalAgent](facetBuilder = historicalAgentFacets).map { result =>
      Ok(views.html.historicalAgent.list(result,
        portalAgentRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
    Ok(views.html.historicalAgent.show(request.item, request.annotations, request.links, request.watched))
  }

  def export(id: String) = OptionalUserAction.async { implicit request =>
    val format = "eac"
    val params = request.queryString.filterKeys(_ == "lang")
    userBackend.stream(s"classes/${EntityType.HistoricalAgent}/$id/$format", params = params).map { case (head, body) =>
      Status(head.status)
        .chunked(body.andThen(Enumerator.eof))
        .withHeaders(head.headers.map(s => (s._1, s._2.head)).toSeq: _*)
    }
  }
}
