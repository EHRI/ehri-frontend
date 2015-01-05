package controllers.portal

import backend.{Backend, IdGenerator}
import com.google.inject.{Inject, Singleton}
import controllers.base.SessionPreferences
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{Concept, AccountDAO, Country, Repository}
import play.api.libs.concurrent.Execution.Implicits._
import utils.SessionPrefs
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Concepts @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                                  userDAO: AccountDAO, idGenerator: IdGenerator)
  extends PortalController
  with Generic[Concept]
  with Search
  with FacetConfig
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  private val portalConceptRoutes = controllers.portal.routes.Concepts

  def searchAll = UserBrowseAction.async { implicit request =>
    find[Concept](
      entities = List(EntityType.Concept),
      facetBuilder = conceptFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.concept.list(page, params, facets,
        portalConceptRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    find[Concept](
      filters = Map("parentId" -> request.item.id),
      facetBuilder = conceptFacets,
      entities = List(EntityType.Concept)
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.concept.show(request.item, page, params, facets,
        portalConceptRoutes.browse(id), request.annotations, request.links, request.watched))
    }
  }
}
