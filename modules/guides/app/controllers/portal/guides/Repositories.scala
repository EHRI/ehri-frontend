package controllers.portal.guides

import javax.inject._

import services.cypher.CypherService
import controllers.AppComponents
import controllers.portal.base.{Generic, PortalController}
import models.{GuidePage, _}
import play.api.mvc.{Action, AnyContent, ControllerComponents}


@Singleton
case class Repositories @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  guides: GuideService,
  cypher: CypherService
) extends PortalController
  with Generic[Repository] {

  def browse(path: String, id: String): Action[AnyContent] = GetItemAction(id).apply { implicit request =>
    itemOr404(guides.find(path, activeOnly = true)) { guide =>
      Ok(views.html.guides.repository(guide, GuidePage.repository(Some(request.item.toStringLang)), guides.findPages(guide), request.item))
    }
  }
}