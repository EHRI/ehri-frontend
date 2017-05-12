package controllers.admin

import javax.inject._

import backend.Readable
import controllers.Components
import controllers.base.AdminController
import models.base.AnyModel
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent}


case class Data @Inject()(
  components: Components,
  ws: WSClient
) extends AdminController {

  implicit val rd: Readable[AnyModel] = AnyModel.Converter

  private def passThroughHeaders(headers: Map[String, Seq[String]],
                                 filter: Seq[String] = Seq.empty): Seq[(String, String)] = {
    headers.filter(kv => if (filter.isEmpty) true else filter.contains(kv._1)).flatMap { case (k, seq) =>
      seq.map(s => k -> s)
    }.toSeq
  }

  def getItem(id: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    implicit val rd: Readable[AnyModel] = AnyModel.Converter
    userDataApi.fetch(List(id)).map {
      case Some(mm) :: _ => views.admin.Helpers.linkToOpt(mm)
        .map(Redirect) getOrElse NotFound(views.html.errors.itemNotFound())
      case _ => NotFound(views.html.errors.itemNotFound())
    }
  }

  def getItemType(entityType: defines.EntityType.Value, id: String) = OptionalUserAction { implicit request =>
    views.admin.Helpers.linkToOpt(entityType, id)
      .map(Redirect)
      .getOrElse(NotFound(views.html.errors.itemNotFound()))
  }

  def getItemRawJson(entityType: defines.EntityType.Value, id: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    userDataApi.query(s"classes/$entityType/$id").map { r =>
      Ok(r.json)
    }
  }

  def forward(urlPart: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val url = urlPart + (if(request.rawQueryString.trim.isEmpty) "" else "?" + request.rawQueryString)
    userDataApi.stream(url).map { sr =>
      val result:Status = Status(sr.headers.status)
      val rHeaders = passThroughHeaders(sr.headers.headers)
      val ct = sr.headers.headers.get(HeaderNames.CONTENT_TYPE)
        .flatMap(_.headOption).getOrElse(ContentTypes.JSON)
      result.chunked(sr.body).as(ct).withHeaders(rHeaders: _*)
    }
  }
}