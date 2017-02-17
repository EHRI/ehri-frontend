package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Group
import play.api.mvc.{Action, AnyContent}


@Singleton
case class Groups @Inject()(
  components: Components,
  cypher: Cypher
) extends PortalController
  with Generic[Group]
  with Search {

  def browse(id: String): Action[AnyContent] = GetItemAction(id).apply { implicit request =>
    Ok(views.html.group.show(request.item))
  }
}
