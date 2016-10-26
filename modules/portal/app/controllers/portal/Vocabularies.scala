package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.{Concept, Vocabulary}
import utils.search._

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Vocabularies @Inject()(
  components: Components,
  cypher: Cypher
) extends PortalController
  with Generic[Vocabulary]
  with Search
  with FacetConfig {

  private val portalVocabRoutes = controllers.portal.routes.Vocabularies

  def searchAll = UserBrowseAction.async { implicit request =>
    findType[Vocabulary](
      facetBuilder = repositorySearchFacets
    ).map { result =>
      Ok(views.html.vocabulary.list(result, portalVocabRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.vocabulary.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[Concept](
      filters = Map(SearchConstants.HOLDER_ID -> id),
      facetBuilder = conceptFacets
    ).map { result =>
      Ok(views.html.vocabulary.show(request.item, result, request.annotations,
        request.links, portalVocabRoutes.search(id), request.watched))
    }
  }

  def search(id: String) = GetItemAction(id).async { implicit request =>
    findType[Concept](
      filters = Map(SearchConstants.HOLDER_ID -> id),
      facetBuilder = conceptFacets
    ).map { result =>
      if (isAjax) Ok(views.html.vocabulary.childItemSearch(request.item, result,
        portalVocabRoutes.search(id), request.watched))
      else Ok(views.html.vocabulary.search(request.item, result,
        portalVocabRoutes.search(id), request.watched))
    }
  }
}
