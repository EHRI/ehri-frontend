package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.DocumentaryUnit
import models.base.AnyModel
import play.api.mvc.RequestHeader
import utils.search._

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class DocumentaryUnits @Inject()(
  components: Components,
  cypher: Cypher
) extends PortalController
  with Generic[DocumentaryUnit]
  with Search
  with FacetConfig {

  private val portalDocRoutes = controllers.portal.routes.DocumentaryUnits

  private def filterKey(implicit request: RequestHeader): String =
    if (!hasActiveQuery(request)) SearchConstants.PARENT_ID else SearchConstants.ANCESTOR_IDS

  def searchAll = UserBrowseAction.async { implicit request =>
    val filters = if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[AnyModel](
      filters = filters,
      facetBuilder = docSearchFacets,
      entities = Seq(EntityType.DocumentaryUnit, EntityType.VirtualUnit)
    ).map { result =>
      Ok(views.html.documentaryUnit.list(result, portalDocRoutes.searchAll(),
        request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.documentaryUnit.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[DocumentaryUnit](
      filters = Map(filterKey -> request.item.id),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      Ok(views.html.documentaryUnit.show(request.item, result,  request.annotations,
        request.links, portalDocRoutes.search(id), request.watched))
    }
  }

  def search(id: String) = GetItemAction(id).async { implicit request =>
    findType[DocumentaryUnit](
      filters = Map(filterKey -> request.item.id),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      if (isAjax) Ok(views.html.documentaryUnit.childItemSearch(request.item, result,
        portalDocRoutes.search(id), request.watched))
      else Ok(views.html.documentaryUnit.search(request.item, result,
        portalDocRoutes.search(id), request.watched))
    }
  }

  def export(id: String, asFile: Boolean) = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.DocumentaryUnit, id, Seq("ead"), asFile)
  }
}
