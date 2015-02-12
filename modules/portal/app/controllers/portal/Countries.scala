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
case class Countries @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
                                  accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends PortalController
  with Generic[Country]
  with Search
  with FacetConfig {

  private val portalCountryRoutes = controllers.portal.routes.Countries

  def searchAll = UserBrowseAction.async { implicit request =>
    findType[Country](facetBuilder = countryFacets).map { result =>
      Ok(p.country.list(result, portalCountryRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
      if (isAjax) Ok(p.country.itemDetails(request.item, request.annotations, request.links, request.watched))
      else Ok(p.country.show(request.item, request.annotations, request.links, request.watched))
  }

  def search(id: String) = GetItemAction(id).async {  implicit request =>
      findType[Repository](
        filters = Map(SearchConstants.COUNTRY_CODE -> request.item.id),
        facetBuilder = localRepoFacets
      ).map { result =>
        if (isAjax) Ok(p.country.childItemSearch(request.item, result,
          portalCountryRoutes.search(id), request.watched))
        else Ok(p.country.search(request.item, result,
          portalCountryRoutes.search(id), request.watched))
      }
  }
}
