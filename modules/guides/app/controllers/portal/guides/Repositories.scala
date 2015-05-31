package controllers.portal.guides

import auth.AccountManager
import backend.Backend
import javax.inject._
import controllers.portal.base.{Generic, PortalController}
import models.{Guide, GuidePage, _}
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.search.{SearchEngine, SearchItemResolver}


@Singleton
case class Repositories @Inject()(implicit app: play.api.Application, cache: CacheApi, globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
                            accounts: AccountManager, pageRelocator: utils.MovedPageLookup, messagesApi: MessagesApi, guideDAO: GuideDAO)
  extends PortalController
  with Generic[Repository] {

  def browse(path: String, id: String) = GetItemAction(id).apply { implicit request =>
    itemOr404(guideDAO.find(path, activeOnly = true)) { guide =>
      Ok(views.html.guides.repository(guide, GuidePage.repository(Some(request.item.toStringLang)), guideDAO.findPages(guide), request.item))
    }
  }
}