package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Concept
import play.api.mvc.{Action, AnyContent}
import utils.PageParams
import utils.search._


@Singleton
case class Concepts @Inject()(
  components: Components,
  cypher: Cypher,
  fc: FacetConfig
) extends PortalController
  with Generic[Concept]
  with Search {

  private val portalConceptRoutes = controllers.portal.routes.Concepts

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[Concept](params, paging, facetBuilder = fc.conceptFacets).map { result =>
      Ok(views.html.concept.list(result,
        portalConceptRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[Concept](params, paging,
      filters = Map(SearchConstants.PARENT_ID -> request.item.id)).map { result =>
      Ok(views.html.concept.show(request.item, result,
        portalConceptRoutes.browse(id), request.annotations, request.links, request.watched))
    }
  }
}
