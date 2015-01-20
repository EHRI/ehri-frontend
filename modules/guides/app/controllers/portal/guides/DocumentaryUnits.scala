package controllers.portal.guides

import auth.AccountManager
import backend.Backend
import com.google.inject._
import controllers.portal.base.{Generic, PortalController}
import models.{Guide, GuidePage, _}
import utils.search.{Resolver, Dispatcher}
import views.html.p


@Singleton
case class DocumentaryUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            userDAO: AccountManager)
  extends PortalController
  with Generic[DocumentaryUnit] {

  def browse(path: String, id: String) = GetItemAction(id).apply { implicit request =>
    itemOr404(Guide.find(path, activeOnly = true)) { guide =>
      Ok(p.guides.documentaryUnit(
        request.item,
        request.annotations,
        request.links,
        request.watched,
        GuidePage.document(Some(request.item.toStringLang)) -> (guide -> guide.findPages))
      )
    }
  }
}