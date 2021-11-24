package models

import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.libs.json.{Format, Json}

import java.util.Base64

case class BasicAuthConfig(username: String, password: String) {
  def encodeBase64: String = Base64.getEncoder.encodeToString((username + ":" + password).getBytes)
}

object BasicAuthConfig {
  final val USERNAME = "username"
  final val PASSWORD = "password"

  implicit val _format: Format[BasicAuthConfig] = Json.format[BasicAuthConfig]

  val form: Form[BasicAuthConfig] = Form(mapping(
    USERNAME -> nonEmptyText,
    PASSWORD -> nonEmptyText,
  )(BasicAuthConfig.apply)(BasicAuthConfig.unapply))
}

