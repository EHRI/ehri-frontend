package controllers.portal.guides

import auth.AccountManager
import backend.DataApi
import javax.inject._
import backend.rest.cypher.Cypher
import controllers.portal.base.{Generic, PortalController}
import models.{Guide, GuidePage, _}
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.search.{SearchEngine, SearchItemResolver}
import views.MarkdownRenderer


@Singleton
case class Repositories @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: utils.MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  guides: GuideService,
  cypher: Cypher
) extends PortalController
  with Generic[Repository] {

  def browse(path: String, id: String) = GetItemAction(id).apply { implicit request =>
    itemOr404(guides.find(path, activeOnly = true)) { guide =>
      Ok(views.html.guides.repository(guide, GuidePage.repository(Some(request.item.toStringLang)), guides.findPages(guide), request.item))
    }
  }
}