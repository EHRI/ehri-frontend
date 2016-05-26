package controllers.portal

import auth.AccountManager
import backend.DataApi
import backend.rest.cypher.Cypher
import javax.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.{Repository, Country}
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import utils.search._
import views.MarkdownRenderer

import scala.concurrent.Future.{successful => immediate}

@Singleton
case class Countries @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: utils.MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends PortalController
  with Generic[Country]
  with Search
  with FacetConfig {

  private val portalCountryRoutes = controllers.portal.routes.Countries

  def searchAll = UserBrowseAction.async { implicit request =>
    findType[Country](
      facetBuilder = countryFacets,
      defaultOrder = SearchOrder.Name
    ).map { result =>
      Ok(views.html.country.list(result, portalCountryRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.country.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else {
      findType[Repository](
        filters = Map(SearchConstants.COUNTRY_CODE -> request.item.id),
        facetBuilder = localRepoFacets,
        defaultOrder = SearchOrder.Name
      ).map { result =>
        Ok(views.html.country.show(request.item, result, request.annotations,
          request.links, portalCountryRoutes.search(id), request.watched))
      }
    }
  }

  def search(id: String) = GetItemAction(id).async {  implicit request =>
    findType[Repository](
      filters = Map(SearchConstants.COUNTRY_CODE -> request.item.id),
      facetBuilder = localRepoFacets,
      defaultOrder = SearchOrder.Name
    ).map { result =>
      if (isAjax) Ok(views.html.country.childItemSearch(request.item, result,
        portalCountryRoutes.search(id), request.watched))
      else Ok(views.html.country.search(request.item, result,
        portalCountryRoutes.search(id), request.watched))
    }
  }
}
