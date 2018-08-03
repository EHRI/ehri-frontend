package controllers.portal

import javax.inject.{Inject, Singleton}

import services.cypher.CypherService
import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Link
import play.api.mvc.{Action, AnyContent, ControllerComponents}


@Singleton
case class Links @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: CypherService
) extends PortalController
  with Generic[Link]
  with Search {

  def browse(id: String): Action[AnyContent] = GetItemAction(id).apply { implicit request =>
    Ok(views.html.link.show(request.item))
  }
}
