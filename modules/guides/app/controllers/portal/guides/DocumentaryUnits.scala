package controllers.portal.guides

import backend.Backend
import com.google.inject._
import controllers.base.SessionPreferences
import controllers.portal.base.{Generic, PortalController}
import controllers.portal.Secured
import models.{Guide, GuidePage, _}
import utils._
import utils.search.{Resolver, Dispatcher}
import views.html.p


@Singleton
case class DocumentaryUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            userDAO: AccountDAO)
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