package client.json

import play.api.libs.json.Writes

trait ClientWriteable[T] {
  def _clientFormat: Writes[T]
}
