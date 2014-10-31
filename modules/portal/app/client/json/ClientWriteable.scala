package client.json

import play.api.libs.json.Writes

trait ClientWriteable[T] {
  val clientFormat: Writes[T]
}
