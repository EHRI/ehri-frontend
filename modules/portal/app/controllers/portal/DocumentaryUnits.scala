package controllers.portal

import auth.AccountManager
import play.api.libs.concurrent.Execution.Implicits._
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.DocumentaryUnit
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class DocumentaryUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                                  accounts: AccountManager)
  extends PortalController
  with Generic[DocumentaryUnit]
  with Search
  with FacetConfig {

  private val portalDocRoutes = controllers.portal.routes.DocumentaryUnits

  def searchAll = UserBrowseAction.async { implicit request =>
    val filters = if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[DocumentaryUnit](
      filters = filters,
      entities = List(EntityType.DocumentaryUnit),
      facetBuilder = docSearchFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.documentaryUnit.list(page, params, facets, portalDocRoutes.searchAll(),
        request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
      if (isAjax) Ok(p.documentaryUnit.itemDetails(request.item, request.annotations, request.links, request.watched))
      else Ok(p.documentaryUnit.show(request.item, request.annotations, request.links, request.watched))
  }

  def search(id: String) = GetItemAction(id).async { implicit request =>
      find[DocumentaryUnit](
        filters = Map(SearchConstants.PARENT_ID -> request.item.id),
        entities = List(EntityType.DocumentaryUnit),
        facetBuilder = localDocFacets,
        defaultOrder = SearchOrder.Id
      ).map { case QueryResult(page, params, facets) =>
        if (isAjax) Ok(p.documentaryUnit.childItemSearch(request.item, page, params, facets,
          portalDocRoutes.search(id), request.watched))
        else Ok(p.documentaryUnit.search(request.item, page, params, facets,
          portalDocRoutes.search(id), request.watched))
      }
  }
}
