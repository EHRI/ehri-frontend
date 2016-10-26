package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Group


@Singleton
case class Groups @Inject()(
  components: Components,
  cypher: Cypher
) extends PortalController
  with Generic[Group]
  with Search
  with FacetConfig {

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
    Ok(views.html.group.show(request.item))
  }
}
