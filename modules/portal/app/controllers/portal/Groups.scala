package controllers.portal

import javax.inject.{Inject, Singleton}

import services.cypher.CypherService
import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Group
import play.api.mvc.{Action, AnyContent, ControllerComponents}


@Singleton
case class Groups @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: CypherService
) extends PortalController
  with Generic[Group]
  with Search {

  def browse(id: String): Action[AnyContent] = GetItemAction(id).apply { implicit request =>
    Ok(views.html.group.show(request.item))
  }
}
