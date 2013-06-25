package models.base

import play.api.libs.json.JsObject

/**
 * Created by mike on 23/06/13.
 */
trait MetaModel[T] {
  val model: T
}
