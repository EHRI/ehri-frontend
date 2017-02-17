package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{DocumentaryUnit, Repository}
import play.api.mvc.{Action, AnyContent, RequestHeader}
import utils.PageParams
import utils.search._

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Repositories @Inject()(
  components: Components,
  cypher: Cypher,
  fc: FacetConfig
) extends PortalController
  with Generic[Repository]
  with Search {

  private val portalRepoRoutes = controllers.portal.routes.Repositories

  private def filters(id: String)(implicit request: RequestHeader): Map[String, Any] =
    (if (!hasActiveQuery(request)) Map(SearchConstants.TOP_LEVEL -> true)
    else Map.empty[String, Any]) ++ Map(SearchConstants.HOLDER_ID -> id)


  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[Repository](params, paging, facetBuilder = fc.repositorySearchFacets,
      sort = SearchSort.Name).map { result =>
      Ok(views.html.repository.list(result, portalRepoRoutes.searchAll(),
        request.watched))
    }
  }

  def searchAllByCountry(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[Repository](params, paging, facetBuilder = fc.repositorySearchFacets,
      sort = SearchSort.Country).map { result =>
      Ok(views.html.repository.listByCountry(result,
        portalRepoRoutes.searchAllByCountry(),
        request.watched))
    }
  }

  def browse(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.repository.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[DocumentaryUnit](params, paging, filters = filters(request.item.id),
      facetBuilder = fc.localDocFacets, sort = SearchSort.Id).map { result =>
      Ok(views.html.repository.show(request.item, result, request.annotations,
        request.links, portalRepoRoutes.search(id), request.watched))
    }
  }

  def search(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[DocumentaryUnit](params, paging, filters = filters(request.item.id),
      facetBuilder = fc.localDocFacets, sort = SearchSort.Id).map { result =>
      if (isAjax) Ok(views.html.repository.childItemSearch(request.item, result,
        portalRepoRoutes.search(id), request.watched))
      else Ok(views.html.repository.search(request.item, result,
        portalRepoRoutes.search(id), request.watched))
    }
  }

  def export(id: String, asFile: Boolean): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.Repository, id, Seq("eag", "ead"), asFile)
  }
}
