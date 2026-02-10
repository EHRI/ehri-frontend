package controllers.admin

import javax.inject._
import controllers.AppComponents
import controllers.base.AdminController
import models.{EntityType, Model, Readable}
import play.api.cache.Cached
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, ControllerComponents, EssentialAction}

import scala.concurrent.duration.DurationInt


case class Data @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  ws: WSClient,
  cached: Cached
) extends AdminController {

  implicit val rd: Readable[Model] = Model.Converter

  private def passThroughHeaders(headers: Map[String, scala.collection.Seq[String]],
                                 filter: Seq[String] = Seq.empty): Seq[(String, String)] = {
    headers.filter(kv => if (filter.isEmpty) true else filter.contains(kv._1)).flatMap { case (k, seq) =>
      seq.map(s => k -> s)
    }.toSeq
  }

  def i18n(): EssentialAction = cached.status(_ => "admin:i18n", OK, 72.hours) {
    Action {
      val messages = messagesApi.messages
      val jsonData = Json.toJson(messages)
      val etag = s""""${jsonData.hashCode().toString}""""

      Ok(jsonData).withHeaders(
        CACHE_CONTROL -> "public, max-age=259200",
        ETAG -> etag
      )
    }
  }

  def getItem(id: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    implicit val rd: Readable[Model] = Model.Converter
    userDataApi.fetch(List(id)).map {
      case Some(mm) :: _ => views.admin.Helpers.linkToOpt(mm)
        .map(Redirect) getOrElse NotFound(views.html.errors.itemNotFound())
      case _ => NotFound(views.html.errors.itemNotFound())
    }
  }

  def getItemType(entityType: EntityType.Value, id: String): Action[AnyContent] = OptionalUserAction { implicit request =>
    views.admin.Helpers.linkToOpt(entityType, id)
      .map(Redirect)
      .getOrElse(NotFound(views.html.errors.itemNotFound()))
  }

  def getItemRawJson(entityType: EntityType.Value, id: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    userDataApi.query(s"classes/$entityType/$id").map { r =>
      Ok(r.json)
    }
  }

  def forward(urlPart: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val url = urlPart + (if(request.rawQueryString.trim.isEmpty) "" else "?" + request.rawQueryString)
    userDataApi.query(url).map { sr =>
      val result:Status = Status(sr.status)
      val rHeaders = passThroughHeaders(sr.headers)
      val ct = sr.headers.get(HeaderNames.CONTENT_TYPE)
        .flatMap(_.headOption).getOrElse(ContentTypes.JSON)
      result.chunked(sr.bodyAsSource).as(ct).withHeaders(rHeaders: _*)
    }
  }
}
