package controllers.portal.guides

import auth.AccountManager
import backend.Backend
import com.google.inject._
import controllers.portal.base.{Generic, PortalController}
import models.{Guide, GuidePage, _}
import utils.search.{SearchEngine, SearchItemResolver}
import views.html.p


@Singleton
case class Repositories @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
                            accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends PortalController
  with Generic[Repository] {

  def browse(path: String, id: String) = GetItemAction(id).apply { implicit request =>
    itemOr404(Guide.find(path, activeOnly = true)) { guide =>
      Ok(p.guides.repository(guide, GuidePage.repository(Some(request.item.toStringLang)), guide.findPages(), request.item))
    }
  }
}