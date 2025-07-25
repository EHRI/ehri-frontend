package models

import play.api.mvc.{Call, RequestHeader}

case class OgMeta(title: String, url: Call, description: Option[String], image: Option[String] = None) {
  def toMap(implicit req: RequestHeader): Map[String, String] = {
    Map(
      "title" -> title,
      "url" -> url.absoluteURL(req.secure),
      "description" -> description.getOrElse(""),
      "image" -> image.getOrElse("")
    )
  }
}
