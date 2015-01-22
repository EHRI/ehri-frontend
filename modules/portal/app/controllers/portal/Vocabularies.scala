package controllers.portal

import auth.AccountManager
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{Vocabulary, Concept}
import play.api.libs.concurrent.Execution.Implicits._
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Vocabularies @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                                  accounts: AccountManager)
  extends PortalController
  with Generic[Vocabulary]
  with Search
  with FacetConfig {

  private val portalVocabRoutes = controllers.portal.routes.Vocabularies

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
      if (isAjax) Ok(p.vocabulary.itemDetails(request.item, request.annotations, request.links, request.watched))
      else Ok(p.vocabulary.show(request.item, request.annotations, request.links, request.watched))
  }

  def search(id: String) = GetItemAction(id).async { implicit request =>
      val filters = Map(
        SearchConstants.HOLDER_ID -> request.item.id,
        SearchConstants.TOP_LEVEL -> true.toString
      )
      find[Concept](
        filters = filters,
        entities = List(EntityType.Concept),
        facetBuilder = conceptFacets
      ).map { case QueryResult(page, params, facets) =>
        if (isAjax) Ok(p.vocabulary.childItemSearch(request.item, page, params, facets,
          portalVocabRoutes.search(id), request.watched))
        else Ok(p.vocabulary.search(request.item, page, params, facets,
          portalVocabRoutes.search(id), request.watched))
      }
  }

}
