package models

import play.api.libs.json.{JsObject, JsString}

trait PersistentIdentifiable {
  def meta: JsObject

  def pid: Option[String] = meta.fields.collectFirst { case "pid" -> JsString(pid) => pid }
}
