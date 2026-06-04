package controllers.api.v1

import controllers.AppComponents
import controllers.portal.base.PortalController
import models.api.v1.JsonApiV1._
import play.api.libs.json._
import play.api.mvc._

import javax.inject.{Inject, Singleton}


@Singleton
case class ApiV1Home @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents
) extends PortalController {

  private val apiRoutes = controllers.api.v1.routes.ApiV1

  private val AcceptsJsonApi = Accepting(JSONAPI_MIMETYPE)

  def index(): Action[AnyContent] = OptionalUserAction { implicit request =>
    render {
      case Accepts.Json() | AcceptsJsonApi() => Ok(
        Json.obj(
          "meta" -> Json.obj(
            "name" -> "EHRI API V1",
            "routes" -> Json.obj(
              "search" -> (apiRoutes.search().absoluteURL() + "?[q=Text Query]"),
              "fetch" -> apiRoutes.fetch("ITEM-ID").absoluteURL(),
              "search-in" -> (apiRoutes.searchIn("ITEM-ID").absoluteURL() + "?[q=Text Query]")
            ),
            "status" -> config.get[String]("ehri.api.v1.status")
          ),
          "jsonapi" -> Json.obj(
            "version" -> config.get[String]("ehri.api.v1.version")
          )
        )
      ).as(JSONAPI_MIMETYPE)
      case _ => Ok(views.html.api.v1.docs())
    }
  }
}
