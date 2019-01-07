package controllers.api.v1

import controllers.AppComponents
import controllers.portal.base.PortalController
import javax.inject.{Inject, Singleton}
import models.api.v1.JsonApiV1._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc._


@Singleton
case class ApiV1Home @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents
) extends PortalController {

  private val apiRoutes = controllers.api.v1.routes.ApiV1

  private val AcceptsJsonApi = Accepting(JSONAPI_MIMETYPE)

  private def error(status: Int, message: Option[String] = None)(implicit requestHeader: RequestHeader): Result =
    Status(status)(errorJson(status, message))

  private def errorJson(status: Int, message: Option[String] = None)(implicit requestHeader: RequestHeader): JsObject = {
    Json.obj(
      "errors" -> Json.arr(
        JsonApiError(
          status = status.toString,
          title = Messages(s"api.error.$status"),
          detail = message
        )
      )
    )
  }

  def index() = OptionalUserAction { implicit request =>
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
            "status" -> "ALPHA: Do not use for production"
          ),
          "jsonapi" -> Json.obj(
            "version" -> "1.0"
          )
        )
      ).as(JSONAPI_MIMETYPE)
      case _ => Ok(views.html.api.v1.docs())
    }
  }
}
