package controllers.portal.guides

import javax.inject._

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.SearchType
import controllers.portal.FacetConfig
import controllers.portal.base.{Generic, PortalController}
import models.{GuidePage, _}
import play.api.mvc.{Action, AnyContent}
import utils.PageParams
import utils.search.{SearchConstants, SearchParams}


@Singleton
case class DocumentaryUnits @Inject()(
  components: Components,
  guides: GuideService,
  cypher: Cypher,
  fc: FacetConfig
) extends PortalController
  with Generic[DocumentaryUnit]
  with SearchType[DocumentaryUnit] {

  def browse(path: String, id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    futureItemOr404 {
      guides.find(path, activeOnly = true).map { guide =>
        val filterKey = if (!hasActiveQuery(request)) SearchConstants.PARENT_ID
          else SearchConstants.ANCESTOR_IDS

        findType[DocumentaryUnit](params, paging, filters = Map(filterKey -> request.item.id), facetBuilder = fc.docSearchFacets).map { result =>
          Ok(views.html.guides.documentaryUnit(
            guide,
            GuidePage.document(Some(request.item.toStringLang)),
            guides.findPages(guide),
            request.item,
            result,
            controllers.portal.guides.routes.DocumentaryUnits.browse(path, id),
            request.annotations,
            request.links,
            request.watched
          ))
        }
      }
    }
  }
}