package models.json

import play.api.libs.json.Writes

trait ClientWriteable[T] {
  val clientFormat: Writes[T]
}
