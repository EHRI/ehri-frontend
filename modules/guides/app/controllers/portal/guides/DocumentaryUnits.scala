package controllers.portal.guides

import auth.AccountManager
import backend.Backend
import com.google.inject._
import controllers.generic.SearchType
import controllers.portal.FacetConfig
import controllers.portal.base.{Generic, PortalController}
import models.{Guide, GuidePage, _}
import utils.search.{SearchConstants, SearchItemResolver, SearchEngine}
import views.html.p

import play.api.libs.concurrent.Execution.Implicits._

@Singleton
case class DocumentaryUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
                            accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends PortalController
  with Generic[DocumentaryUnit]
  with SearchType[DocumentaryUnit]
  with FacetConfig {

  def browse(path: String, id: String) = GetItemAction(id).async { implicit request =>
    futureItemOr404 {
      Guide.find(path, activeOnly = true).map { guide =>
        val filterKey = if (!hasActiveQuery(request)) SearchConstants.PARENT_ID
          else SearchConstants.ANCESTOR_IDS

        findType[DocumentaryUnit](
          filters = Map(filterKey -> request.item.id),
          facetBuilder = docSearchFacets
        ).map { result =>
          Ok(p.guides.documentaryUnit(
            guide,
            GuidePage.document(Some(request.item.toStringLang)),
            guide.findPages(),
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