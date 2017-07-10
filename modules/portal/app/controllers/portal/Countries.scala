package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{Country, Repository}
import play.api.mvc.{Action, AnyContent}
import utils.PageParams
import utils.search._

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Countries @Inject()(
  components: Components,
  cypher: Cypher,
  fc: FacetConfig
) extends PortalController
  with Generic[Country]
  with Search {

  private val portalCountryRoutes = controllers.portal.routes.Countries

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[Country](params, paging, facetBuilder = fc.countryFacets, sort = SearchSort.Name).map { result =>
      Ok(views.html.country.list(result, portalCountryRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.country.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[Repository](params, paging,
      filters = Map(SearchConstants.COUNTRY_CODE -> request.item.id),
      facetBuilder = fc.localRepoFacets, sort = SearchSort.Name).map { result =>
      Ok(views.html.country.show(request.item, result, request.annotations,
        request.links, portalCountryRoutes.search(id), request.watched))
    }
  }

  def search(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[Repository](params, paging,
      filters = Map(SearchConstants.COUNTRY_CODE -> request.item.id),
      facetBuilder = fc.localRepoFacets, sort = SearchSort.Name).map { result =>
      if (isAjax) Ok(views.html.country.childItemSearch(request.item, result,
        portalCountryRoutes.search(id), request.watched))
      else Ok(views.html.country.search(request.item, result,
        portalCountryRoutes.search(id), request.watched))
    }
  }

  def export(id: String, asFile: Boolean): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.Country, id, Seq("eag"), asFile)
  }
}
