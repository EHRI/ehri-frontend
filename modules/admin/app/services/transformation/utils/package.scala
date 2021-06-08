package services.transformation

import play.api.libs.Codecs
import models.DataTransformation
import play.api.libs.json.{JsObject, Json}

package object utils {

  /**
    * Given a set of transformations calculate a unique tag/digest for this
    * operation.
    *
    * @param mappings a set of transform operations
    * @return an md5 string representing the transformations
    */
  def digest(mappings: Seq[(DataTransformation.TransformationType.Value, String, JsObject)]): String = {
    val sb = new StringBuilder
    mappings.foreach { case (mapType, map, params) =>
      sb.append(mapType)
      sb.append(map)
      sb.append(Json.stringify(params))
    }
    Codecs.md5(sb.toString())
  }

  /**
    * Given a string of input source and a set of transformations, calculate
    * a unique tag/digest for this operation.
    *
    * @param src      the input data, or a hash thereof
    * @param mappings a set of transform operations
    * @return an md5 string representing the overall operation
    */
  def digest(src: String, mappings: Seq[(DataTransformation.TransformationType.Value, String, JsObject)]): String =
    Codecs.md5(src + digest(mappings))
}
