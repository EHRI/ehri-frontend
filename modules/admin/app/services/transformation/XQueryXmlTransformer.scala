package services.transformation

import com.google.inject.ImplementedBy
import play.api.libs.json.{JsObject, Json}

@ImplementedBy(classOf[BaseXXQueryXmlTransformer])
trait XQueryXmlTransformer {
  def transform(input: String, mapping: String, params: JsObject = Json.obj()): String
}
