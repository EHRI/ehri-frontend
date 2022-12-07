package controllers.base

import play.api.libs.json.JsError.toJson
import play.api.libs.json.{Json, Reads}
import play.api.mvc.BodyParser

trait ApiBodyParsers {

  self: AdminController =>

  // To override the max request size we unfortunately need to define our own body parser here:
  // The max value is drawn from config:
  protected def apiJson[A](implicit reader: Reads[A]): BodyParser[A] = BodyParser { request =>
    val max = config.get[Long]("ehri.admin.dataManager.maxTransformationSize")
    parse.json(max)(request).map {
      case Left(simpleResult) => Left(simpleResult)
      case Right(jsValue) =>
        jsValue.validate(reader).map { a =>
          Right(a)
        } recoverTotal { jsError =>
          Left(BadRequest(Json.obj("error" -> "invalid", "details" -> toJson(jsError))))
        }
    }
  }
}
