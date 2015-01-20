package controllers.portal

import auth.AccountManager
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{Repository, Country}
import play.api.libs.concurrent.Execution.Implicits._
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Countries @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                                  userDAO: AccountManager)
  extends PortalController
  with Generic[Country]
  with Search
  with FacetConfig {

  private val portalCountryRoutes = controllers.portal.routes.Countries

  def searchAll = UserBrowseAction.async { implicit request =>
    find[Country](entities = List(EntityType.Country),
      facetBuilder = countryFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.country.list(page, params, facets,
        portalCountryRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
      if (isAjax) Ok(p.country.itemDetails(request.item, request.annotations, request.links, request.watched))
      else Ok(p.country.show(request.item, request.annotations, request.links, request.watched))
  }

  def search(id: String) = GetItemAction(id).async {  implicit request =>
      find[Repository](
        filters = Map("countryCode" -> request.item.id),
        facetBuilder = repositorySearchFacets,
        entities = List(EntityType.Repository)
      ).map { case QueryResult(page, params, facets) =>
        if (isAjax) Ok(p.country.childItemSearch(request.item, page, params, facets,
          portalCountryRoutes.search(id), request.watched))
        else Ok(p.country.search(request.item, page, params, facets,
          portalCountryRoutes.search(id), request.watched))
      }
  }
}
