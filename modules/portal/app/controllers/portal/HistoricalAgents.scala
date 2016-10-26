package controllers.portal

import auth.AccountManager
import backend.DataApi
import backend.rest.cypher.Cypher
import javax.inject.{Inject, Singleton}

import auth.handler.AuthHandler
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.HistoricalAgent
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.MovedPageLookup
import utils.search._
import views.MarkdownRenderer

import scala.concurrent.ExecutionContext


@Singleton
case class HistoricalAgents @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  authHandler: AuthHandler,
  executionContext: ExecutionContext,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
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

  def export(id: String, asFile: Boolean) = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.HistoricalAgent, id, Seq("eac"), asFile)
  }
}
