package controllers.portal

import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import javax.inject.{Inject, Singleton}
import models.Link
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.cypher.CypherService


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

  def copies: Action[AnyContent] = OptionalUserAction.apply { implicit request =>
    if (isAjax) Ok.chunked(cypher.legacy(scriptBody =
        """
          |MATCH (d1:DocumentaryUnit)<-[:hasLinkSource]-(link:Link)-[:hasLinkTarget]->
          |       (r2:Repository)<-[:describes]-(rd2:RepositoryDescription),
          |	  (d1)-[:heldBy|childOf*]->(r1:Repository)<-[:describes]-(rd1:RepositoryDescription)
          |WHERE r1 <> r2 AND exists(r1.latitude) AND exists(r2.latitude)
          |RETURN DISTINCT
          |	r1.__id,
          | trim(rd1.name),
          | r1.latitude,
          | r1.longitude,
          | r2.__id,
          | trim(rd2.name),
          | r2.latitude,
          | r2.longitude
          |ORDER BY r1.__id;
        """.stripMargin)
    ) else Ok(views.html.link.copies(controllers.portal.routes.Links.copies()))
  }
}
