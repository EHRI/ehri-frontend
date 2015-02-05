package controllers.portal

import auth.AccountManager
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.Concept
import play.api.libs.concurrent.Execution.Implicits._
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Concepts @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine,
    searchResolver: SearchItemResolver, backend: Backend, accounts: AccountManager)
  extends PortalController
  with Generic[Concept]
  with Search
  with FacetConfig {

  private val portalConceptRoutes = controllers.portal.routes.Concepts

  def searchAll = UserBrowseAction.async { implicit request =>
    find[Concept](
      entities = List(EntityType.Concept),
      facetBuilder = conceptFacets
    ).map { result =>
      Ok(p.concept.list(result,
        portalConceptRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    find[Concept](
      filters = Map("parentId" -> request.item.id),
      facetBuilder = conceptFacets,
      entities = List(EntityType.Concept)
    ).map { result =>
      Ok(p.concept.show(request.item, result,
        portalConceptRoutes.browse(id), request.annotations, request.links, request.watched))
    }
  }
}
