package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Link
import play.api.mvc.{Action, AnyContent}


@Singleton
case class Links @Inject()(
  components: Components,
  cypher: Cypher
) extends PortalController
  with Generic[Link]
  with Search {

  def browse(id: String): Action[AnyContent] = GetItemAction(id).apply { implicit request =>
    Ok(views.html.link.show(request.item))
  }
}
