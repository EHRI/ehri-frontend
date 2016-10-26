package controllers.portal.guides

import javax.inject._

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.portal.base.{Generic, PortalController}
import models.{GuidePage, _}


@Singleton
case class Repositories @Inject()(
  components: Components,
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