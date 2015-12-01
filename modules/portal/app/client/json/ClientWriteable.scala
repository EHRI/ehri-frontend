package client.json

import play.api.libs.json.Writes

trait ClientWriteable[T] {
  def clientFormat: Writes[T]
}
