package controllers.portal

import backend.{Backend, IdGenerator}
import com.google.inject.{Inject, Singleton}
import controllers.base.SessionPreferences
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{HistoricalAgent, AccountDAO, DocumentaryUnit, Repository}
import play.api.libs.concurrent.Execution.Implicits._
import solr.SolrConstants
import utils.SessionPrefs
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class HistoricalAgents @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                                  userDAO: AccountDAO)
  extends PortalController
  with Generic[HistoricalAgent]
  with Search
  with FacetConfig
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  private val portalAgentRoutes = controllers.portal.routes.HistoricalAgents

  def searchAll = UserBrowseAction.async { implicit request =>
    find[HistoricalAgent](
      entities = List(EntityType.HistoricalAgent),
      facetBuilder = historicalAgentFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.historicalAgent.list(page, params, facets,
        portalAgentRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
    Ok(p.historicalAgent.show(request.item, request.annotations, request.links, request.watched))
  }
}
