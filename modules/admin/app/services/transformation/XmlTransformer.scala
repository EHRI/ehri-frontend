package services.transformation

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.google.inject.ImplementedBy
import models.TransformationType
import play.api.libs.json.JsObject

@ImplementedBy(classOf[WrappingXmlTransformer])
trait XmlTransformer {
  def transform(mappings: Seq[(TransformationType.Value, String, JsObject)]): Flow[ByteString, ByteString, _]

  def transform(mapType: TransformationType.Value, map: String, params: JsObject): Flow[ByteString, ByteString, _] =
    transform(Seq((mapType, map, params)))
}
