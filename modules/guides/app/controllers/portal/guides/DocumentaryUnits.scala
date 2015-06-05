package controllers.portal.guides

import auth.AccountManager
import backend.Backend
import javax.inject._
import controllers.generic.SearchType
import controllers.portal.FacetConfig
import controllers.portal.base.{Generic, PortalController}
import models.{Guide, GuidePage, _}
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.search.{SearchConstants, SearchItemResolver, SearchEngine}

import play.api.libs.concurrent.Execution.Implicits._
import views.MarkdownRenderer

@Singleton
case class DocumentaryUnits @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: utils.MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  guideDAO: GuideDAO
) extends PortalController
  with Generic[DocumentaryUnit]
  with SearchType[DocumentaryUnit]
  with FacetConfig {

  def browse(path: String, id: String) = GetItemAction(id).async { implicit request =>
    futureItemOr404 {
      guideDAO.find(path, activeOnly = true).map { guide =>
        val filterKey = if (!hasActiveQuery(request)) SearchConstants.PARENT_ID
          else SearchConstants.ANCESTOR_IDS

        findType[DocumentaryUnit](
          filters = Map(filterKey -> request.item.id),
          facetBuilder = docSearchFacets
        ).map { result =>
          Ok(views.html.guides.documentaryUnit(
            guide,
            GuidePage.document(Some(request.item.toStringLang)),
            guideDAO.findPages(guide),
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