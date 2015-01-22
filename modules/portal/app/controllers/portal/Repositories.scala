package controllers.portal

import auth.AccountManager
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{Repository, DocumentaryUnit}
import play.api.libs.concurrent.Execution.Implicits._
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Repositories @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                                  accounts: AccountManager)
  extends PortalController
  with Generic[Repository]
  with Search
  with FacetConfig {

  private val portalRepoRoutes = controllers.portal.routes.Repositories

  def searchAll = UserBrowseAction.async { implicit request =>
    find[Repository](
      entities = List(EntityType.Repository),
      facetBuilder = repositorySearchFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.repository.list(page, params, facets, portalRepoRoutes.searchAll(),
        request.watched))
    }
  }

  def searchAllByCountry = UserBrowseAction.async { implicit request =>
    find[Repository](
      defaultParams = SearchParams(
        sort = Some(SearchOrder.Country),
        entities = List(EntityType.Repository)),
      facetBuilder = repositorySearchFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.repository.listByCountry(page, params, facets,
        portalRepoRoutes.searchAllByCountry(),
        request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
      if (isAjax) Ok(p.repository.itemDetails(request.item, request.annotations, request.links, request.watched))
      else Ok(p.repository.show(request.item, request.annotations, request.links, request.watched))
  }

  def search(id: String) = GetItemAction(id).async { implicit request =>
    val filters = (if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SearchConstants.TOP_LEVEL -> true)
      else Map.empty[String,Any]) ++ Map(SearchConstants.HOLDER_ID -> request.item.id)

    find[DocumentaryUnit](
      filters = filters,
      entities = List(EntityType.DocumentaryUnit),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { case QueryResult(page, params, facets) =>
        if (isAjax) Ok(p.repository.childItemSearch(request.item, page, params, facets,
          portalRepoRoutes.search(id), request.watched))
        else Ok(p.repository.search(request.item, page, params, facets,
          portalRepoRoutes.search(id), request.watched))
      }
  }
}
