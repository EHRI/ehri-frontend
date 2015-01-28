package controllers.portal.guides

import auth.AccountManager
import backend.Backend
import com.google.inject._
import controllers.portal.base.{Generic, PortalController}
import models.{Guide, GuidePage, _}
import utils.search.{Dispatcher, Resolver}
import views.html.p


@Singleton
case class Repositories @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            accounts: AccountManager)
  extends PortalController
  with Generic[Repository] {

  def browse(path: String, id: String) = GetItemAction(id).apply { implicit request =>
    itemOr404(Guide.find(path, activeOnly = true)) { guide =>
      Ok(p.guides.repository(guide, GuidePage.repository(Some(request.item.toStringLang)), guide.findPages(), request.item))
    }
  }
}