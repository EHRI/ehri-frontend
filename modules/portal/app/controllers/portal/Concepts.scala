package controllers.portal

import auth.AccountManager
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Concept
import play.api.libs.concurrent.Execution.Implicits._
import utils.search._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Concepts @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine,
    searchResolver: SearchItemResolver, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends PortalController
  with Generic[Concept]
  with Search
  with FacetConfig {

  private val portalConceptRoutes = controllers.portal.routes.Concepts

  def searchAll = UserBrowseAction.async { implicit request =>
    findType[Concept](facetBuilder = conceptFacets).map { result =>
      Ok(views.html.concept.list(result,
        portalConceptRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    findType[Concept](
      filters = Map(SearchConstants.PARENT_ID -> request.item.id),
      facetBuilder = conceptFacets
    ).map { result =>
      Ok(views.html.concept.show(request.item, result,
        portalConceptRoutes.browse(id), request.annotations, request.links, request.watched))
    }
  }
}
