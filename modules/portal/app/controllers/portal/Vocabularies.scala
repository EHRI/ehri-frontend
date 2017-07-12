package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.{Concept, Vocabulary}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import utils.PageParams
import utils.search._

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Vocabularies @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: Cypher,
  fc: FacetConfig
) extends PortalController
  with Generic[Vocabulary]
  with Search {

  private val portalVocabRoutes = controllers.portal.routes.Vocabularies

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[Vocabulary](params = params, paging = paging, facetBuilder = fc.repositorySearchFacets).map { result =>
      Ok(views.html.vocabulary.list(result, portalVocabRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.vocabulary.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[Concept](params, paging,
        filters = Map(SearchConstants.HOLDER_ID -> id), facetBuilder = fc.conceptFacets).map { result =>
      Ok(views.html.vocabulary.show(request.item, result, request.annotations,
        request.links, portalVocabRoutes.search(id), request.watched))
    }
  }

  def search(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[Concept](params, paging,
        filters = Map(SearchConstants.HOLDER_ID -> id), facetBuilder = fc.conceptFacets).map { result =>
      if (isAjax) Ok(views.html.vocabulary.childItemSearch(request.item, result,
        portalVocabRoutes.search(id), request.watched))
      else Ok(views.html.vocabulary.search(request.item, result,
        portalVocabRoutes.search(id), request.watched))
    }
  }
}
