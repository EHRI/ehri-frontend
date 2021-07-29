package eu.ehri.project.xml

import play.api.libs.json.{JsObject, Json}

trait XQueryXmlTransformer {
  def transform(input: String, mapping: String, params: JsObject = Json.obj()): String
}
